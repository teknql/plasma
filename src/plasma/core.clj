(ns plasma.core
  "Core namespace for Plasma"
  (:require [systemic.core :as systemic]
            [clojure.core.match :refer [match]]
            [manifold.stream :as s]))

(def ^:dynamic *state*
  "Client state"
  nil)

(def ^:dynamic *resources*
  "Client resources"
  nil)

(defn assoc-state!
  "Associates data in the client state"
  [k data & kvs]
  (when *state*
    (apply swap! *state* assoc k data kvs)))

(defn dissoc-state!
  "Dissociates the provided `k` in state."
  [k & ks]
  (when *state*
    (apply swap! *state* dissoc k ks)))

(defn get-state
  "Gets data from the plasma state.

  Takes an optional `default` if the value doesn't exist."
  ([k] (get-state k nil))
  ([k default]
   (if-not *state*
     default
     (get @*state* k default))))

(defn register-resource!
  "Registers a resource in the client resources"
  ([resource]
   (register-resource! (java.util.UUID/randomUUID) resource))
  ([id resource]
   (when *resources*
     (swap! *resources* assoc id resource)
     id)))

(defn handle
  "Invoke the provided handler and return an `:ok` ,`:stream` or `:error` tuple."
  [event-name args]
  (let [fn-var (if-let [resolved (resolve event-name)]
                 resolved
                 (try
                   (require (-> event-name namespace symbol))
                   (systemic/start!)
                   (resolve event-name)
                   (catch java.io.FileNotFoundException _)))]
    (try
      (let [result (apply fn-var args)]
        (if-not (s/stream? result)
          [:ok result]
          (let [s (s/stream)]
            (s/connect result s {:timeout 100 :downstream? false})
            [:stream s])))
      (catch Exception e
        [:error e]))))

(defn receive!
  "Takes an event-name and args and returns a function that should be invoked with `send!` to
  transport responses."
  [type req-id event-name args]
  (bound-fn [send! on-error]
    (case type
      :plasma/dispose
      (when-some [s (get @*resources* req-id)]
        (s/close! s)
        (swap! *resources* dissoc req-id))
      :plasma/request
      (send!
        (match (handle event-name args)
          [:error (e :guard #(nil? (-> % ex-data :plasma/error)))]
          (do (on-error e event-name args)
              [:error req-id (ex-info "Internal Server" {})])
          [:error  e] [:error req-id e]
          [:stream stream] (do (s/on-closed stream #(send! [:close req-id]))
                               (register-resource! req-id stream)
                               (s/consume #(send! [:stream req-id %]) stream)
                               [:stream-start req-id])
          [:ok resp] [:ok req-id resp])))))

(defn- parse-args
  "Helper for writing macros. Returns the data as a structured map."
  [args]
  (let [[name args]       [(first args) (rest args)]
        [doc-str args]    (if (string? (first args))
                            [(first args) (rest args)]
                            [nil args])
        [attr-map args]   (if (map? (first args))
                            [(first args) (rest args)]
                            [nil args])
        [bindings & body] args
        attr-map          (cond-> (or attr-map {})
                            doc-str (assoc :doc doc-str))]
    {:name     name
     :attr-map attr-map
     :bindings bindings
     :body     body}))

(defmacro defhandler
  "Define a plasma handler"
  {:arglists '([name doc-str? attr-map? [params*] body])}
  [& args]
  (let [{:keys [name attr-map bindings body]} (parse-args args)
        ns-name                               (some-> &env :ns :name str)
        cljs?                                 (some? ns-name)
        req                                   (when cljs?
                                                [`(require '[plasma.client])])
        body                                  (if cljs?
                                                [`(plasma.client/rpc!
                                                    '~(symbol ns-name (str name))
                                                    ~bindings)]
                                                body)]
    `(do ~@req
         (defn ~name
           ~attr-map
           ~bindings
           ~@body))))

(defmacro defstream
  "Define a plasma stream"
  {:arglists '([name doc-str? attr-map? [params*] body])}
  [& args]
  (let [{:keys [name attr-map bindings body]} (parse-args args)
        ns-name                               (some-> &env :ns :name str)
        cljs?                                 (some? ns-name)
        req                                   (when cljs?
                                                [`(require '[plasma.client])])
        body                                  (if cljs?
                                                [`(plasma.client/stream-rpc!
                                                    '~(symbol ns-name (str name))
                                                    ~bindings)]
                                                body)]
    `(do ~@req
         (defn ~name
           ~attr-map
           ~bindings
           ~@body))))
