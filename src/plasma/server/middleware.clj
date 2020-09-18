(ns plasma.server.middleware
  "Middleware for plasma")

(defn auto-require
  "Automatically imports namespaces based off events.

  Optionally takes an `on-required` function which will be called with the event namespace the first
  time it is required."
  ([f] (auto-require f nil))
  ([f on-required]
   (fn [event args]
     (when-not (resolve event)
       (try
         (let [ns (-> event namespace symbol)]
           (require ns)
           (when on-required
             (on-required ns)))
         (catch java.io.FileNotFoundException _)))
     (f event args))))
