(ns plasma.server
  "Namespace for plasma server functions"
  (:require [manifold.stream :as s]
            [plasma.core :as plasma]
            [sieppari.core :as sieparri]
            [clojure.core.match :refer [match]]
            [cognitect.transit :as transit])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))

(defn make-server
  [{:keys [session-atom
           send-fn
           transit-read-handlers
           transit-write-handlers
           transit-format
           interceptors
           on-error]
    :or   {session-atom           (atom {})
           transit-read-handlers  transit/default-read-handlers
           transit-write-handlers transit/default-write-handlers
           transit-format         :json}}]
  {:sessions               session-atom
   :transit-read-handlers  transit-read-handlers
   :transit-write-handlers transit-write-handlers
   :transit-format         transit-format
   :on-error               on-error
   :send-fn                send-fn
   :interceptors           interceptors})

(defn- request-handler
  "Invoke the provided handler and return an `:ok` ,`:stream` or `:error` tuple."
  [{:keys [fn-var args] :as ctx}]
  (try
    (let [result (binding [plasma/*context* ctx]
                   (apply fn-var args))]
      (if-not (s/stream? result)
        [:ok result]
        (let [s (s/stream)]
          (s/connect result s {:timeout 100 :downstream? false})
          [:stream s])))
    (catch Exception e
      [:error e])))

(defn send!
  "Encode the provided `data` and send it to the client with the provided
  `client-handle`."
  [server client-handle data]
  (let [{:keys [transit-format transit-write-handlers send-fn]} server
        out    (ByteArrayOutputStream.)
        writer (transit/writer
                 out
                 transit-format
                 {:handlers transit-write-handlers})]
  (transit/write writer data)
  (send-fn client-handle (.toString out))))

(defn on-connect!
  "Callback to be invoked in an adapter"
  ([server client-handle] (on-connect! server client-handle nil))
  ([server client-handle client-id]
   (swap! (:sessions server) assoc (or client-id client-handle) {})))

(defn on-disconnect!
  "Callback to be invoked on disconnect"
  ([server client-handle] (on-disconnect! server client-handle nil))
  ([server client-handle client-id]
   (binding [plasma/*context* {:server server
                               :client (or client-id client-handle)}]
     (plasma/cleanup-resources!)
     (swap! (:sessions server) dissoc client-handle client-id))))

(defn on-message!
  "Callback to be invoked in an adapter"
  ([server client-handle data]
   (on-message! server client-handle data nil))
  ([server client-handle data client-id]
   (binding [plasma/*context* {:server server
                               :client (or client-id client-handle)}]
     (let [{:keys [transit-read-handlers
                   transit-format
                   on-error
                   interceptors]}              server
           [plasma-msg req-id event-name args] (-> (transit/reader
                                                     (ByteArrayInputStream. (.getBytes data))
                                                     transit-format
                                                     {:handlers transit-read-handlers})
                                                   (transit/read))]
       (case plasma-msg
         :plasma/dispose (plasma/cleanup-resource! req-id)
         :plasma/request
         (send!
           server
           client-handle
           (let [ctx (sieparri/execute-context
                       (conj interceptors request-handler)
                       {:request
                        {:server     server
                         :client     client-handle
                         :event-name event-name
                         :args       args
                         :fn-var     (resolve event-name)}})]
             (match (:response ctx)
                    [:error (e :guard #(nil? (-> % ex-data :plasma/error)))]
                    (do (when on-error
                          (on-error {:error e :ctx ctx}))
                        [:error req-id (ex-info "Internal Server" {})])
                    [:error e] [:error req-id e]
                    [:stream stream]
                    (do
                      (s/on-closed stream #(send! server client-handle [:close req-id]))
                      (plasma/register-resource! req-id #(s/close! stream))
                      (s/consume #(send! server client-handle [:stream req-id %]) stream)
                      [:stream-start req-id])
                    [:ok resp] [:ok req-id resp]))))))))
