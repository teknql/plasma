(ns plasma.client
  (:require [promesa.core :as p]
            [plasma.client.transport :as transport]
            [plasma.client.stream :as s]
            [cognitect.transit :as t]))


(def ^:private state
  "The state of plasma."
  (atom {}))

(defn event?
  "Return true if the provided item matches a plasma event signature.

  Mostly useful for implementing nested transports."
  [msg]
  (and (vector? msg)
       (contains? #{:ok :stream :stream-start :close :error} (first msg))))

(defn receive!
  "Used in implementing transports to handle messages"
  [msg]
  (let [[event event-id data] msg]
    (if-let [known (get @state event-id)]
      (case event
        :ok           (do (p/resolve! known data)
                          (swap! state dissoc event-id))
        :stream       (s/put! known data)
        :stream-start (p/resolve! (:initialized known) true)
        :close        (do (s/close! known)
                          (swap! state dissoc event-id))
        :error        (do (p/reject! known data)
                          (swap! state dissoc event-id)))
      (.warn js/console (str "No pending request found for request ID " event-id)))))

(defn websocket-transport
  "Build a transport using a websocket"
  ([url] (websocket-transport url {}))
  ([url {:keys [on-open on-close on-error
                transit-read-handlers
                transit-write-handlers]}]
   (let [ws     (js/WebSocket. url)
         reader (t/reader :json {:handlers transit-read-handlers})
         writer (t/writer :json {:handlers transit-write-handlers})
         state  (atom {:connected? false :buffer []})
         send-f #(if (:connected? @state)
                   (.send ws (t/write writer %&))
                   (swap! state update :buffer conj %&))]
     (set! (.-onopen ws)
           (fn []
             (swap! state assoc :connected? true)
             (doseq [msg (:buffer @state)]
               (apply send-f msg))
             (swap! state assoc :buffer [])
             (when on-open (on-open))))
     (set! (.-onclose ws) #(do (swap! state assoc :connected? false)
                               (when on-close (on-close))))
     (when on-error
       (set! (.-onerror ws) on-error))
     (set! (.-onmessage ws)
           #(receive! (t/read reader (.-data %))))
     send-f)))

(defn use-transport!
  "Set function used to send requests.

  Called with arity two of the event and and arg vector. The transport should return a promise
  that is resolved when the request completes."
  [f]
  (transport/use! f))

(defn rpc!
  "Send an RPC for the given name and args.

  Return a promesa promise"
  [event args]
  (let [p      (p/deferred)
        req-id (random-uuid)]
    (swap! state assoc req-id p)
    (transport/send! :plasma/request req-id event args)
    p))

(defn stream-rpc!
  "Send an RPC to instantiate a stream.

  Returns a plasma stream."
  [event args]
  (let [{:keys [resource-id] :as s} (s/stream)]
    (swap! state assoc resource-id s)
    (transport/send! :plasma/request resource-id event args)
    s))
