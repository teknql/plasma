(ns plasma.client.transport
  {:no-doc true})


(def ^:private transport
  "The transport used"
  (atom nil))

(defn use!
  "Set function used to send requests.

  Called with arity two of the event and and arg vector. The transport should return a promise
  that is resolved when the request completes."
  [f]
  (reset! transport f))

(defn send!
  "Variadic function to send the provided `args` through the transport"
  [& args]
  (apply @transport args))
