(ns plasma.client.stream
  "Aproximation of manifold streams"
  (:require [plasma.client.transport :as transport]
            [promesa.core :as p]))


(defn stream
  "Return a new stream"
  []
  (let [resource-id (random-uuid)]
    {:closed?     (atom false)
     :resource-id resource-id
     :initialized (p/deferred)
     :fns         (atom [])
     :closed-fns  [#(transport/send! :plasma/dispose resource-id)]}))

(defn- tick!
  "Execute all of the stream functions with the provided item."
  [stream item]
  (let [fns     @(:fns stream)
        new-fns (vec (keep
                       #(when (% item)
                          %)
                       fns))]
    (reset! (:fns stream) new-fns)))

(defn put!
  "Places `item` onto stream.

  Returns `false` if the stream is closed, otherwise `true`."
  [stream item]
  (if @(:closed? stream)
    false
    (do (js/setTimeout #(tick! stream item) 0)
        true)))

(defn consume-via!
  "Adds function `f` to the consumption of `stream`. If `f` returns
  a falsey value, it will stop being called."
  [stream f]
  (swap! (:fns stream) conj f)
  true)

(defn close!
  "Closes the provided stream. Returns a boolean for whether
  the stream was already closed"
  [stream]
  (let [already-closed? @(:closed? stream)]
    (reset! (:closed? stream) true)
    (when-not already-closed?
      (doseq [f (:closed-fns stream)]
        (f)))
    already-closed?))


(defn on-initialized
  "Executes the provided zero arity function when the stream
  has been successfully initialized with the server"
  [s f]
  (p/then
    (:initialized s)
    (fn [_] (f))))
