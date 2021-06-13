(ns plasma.server.interceptors
  "Interceptors for plasma")

(defn auto-require
  "Automatically imports namespaces based off events.

  Optionally takes an `on-required` function which will be called with the event namespace the first
  time it is required."
  ([]
   (auto-require  nil))
  ([on-required]
   {:name ::auto-require
    :enter
    (fn [ctx]
      (let [{:keys [event-name fn-var]} (:request ctx)]
        (if fn-var
          ctx
          (try
            (let [ns (-> event-name namespace symbol)]
              (require ns)
              (when on-required
                (on-required ns)
                (assoc-in ctx [:request fn-var] (resolve event-name))))
            (catch java.io.FileNotFoundException _
              ctx)))))}))

(defn load-metadata
  "Add `:meta` to the request context for the resolved event name.
  Useful for building out authentication middlewares etc."
  []
  {:name ::load-metadata
   :enter
   (fn [ctx]
     (assoc ctx :meta (-> ctx :request :fn-var meta)))})
