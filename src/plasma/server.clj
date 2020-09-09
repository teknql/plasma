(ns plasma.server
  "Namespace for plasma server functions"
  (:require [manifold.stream :as s]
            [plasma.core :as plasma]
            [clojure.core.match :refer [match]]))

(defn handle
  "Invoke the provided handler and return an `:ok` ,`:stream` or `:error` tuple."
  [event-name args]
  (let [fn-var (resolve event-name)]
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
  "Takes an event-name and args and returns a function that should be invoked with `send!` and `on-error`
  to transport responses."
  [type req-id event-name args]
  (bound-fn [send! on-error]
    (case type
      :plasma/dispose
      (plasma/cleanup-resource! req-id)
      :plasma/request
      (send!
        (match (handle event-name args)
          [:error (e :guard #(nil? (-> % ex-data :plasma/error)))]
          (do (on-error e event-name args)
              [:error req-id (ex-info "Internal Server" {})])
          [:error  e] [:error req-id e]
          [:stream stream] (do (s/on-closed stream #(send! [:close req-id]))
                               (plasma/register-resource! req-id #(s/close! stream))
                               (s/consume #(send! [:stream req-id %]) stream)
                               [:stream-start req-id])
          [:ok resp] [:ok req-id resp])))))
