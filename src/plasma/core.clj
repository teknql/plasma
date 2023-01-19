(ns plasma.core
  "Core namespace for Plasma"
  (:require [plasma.macro :as macro]))

(def ^:dynamic *context*
  "Request context for Plasma"
  nil)

(defn assoc-state!
  "Associates data in the client state"
  [k data & kvs]
  (when-some [ctx *context*]
    (let [client   (:client ctx)
          sessions (get-in ctx [:server :sessions])]
      (apply swap! sessions update-in [client :state] assoc k data kvs))))

(defn dissoc-state!
  "Dissociates the provided `k` in state."
  [k & ks]
  (when-some [ctx *context*]
    (let [client   (:client ctx)
          sessions (get-in ctx [:server :sessions])]
      (apply swap! sessions update-in [client :state] dissoc k ks))))

(defn get-state
  "Gets data from the plasma state.

  Takes an optional `default` if the value doesn't exist."
  ([k] (get-state k nil))
  ([k default]
   (when-some [ctx *context*]
    (let [client   (:client ctx)
          sessions (get-in ctx [:server :sessions])]
      (get-in @sessions [client :state k] default)))))

(defn register-resource!
  "Registers a resource in the client resources with the given `id` and returns the `id`.

  Calls `resource-cleanup-fn` on close.

  If no `id` is provided, one will be generated automatically."
  ([resource-cleanup-fn]
   (register-resource! (java.util.UUID/randomUUID) resource-cleanup-fn))
  ([id resource-cleanup-fn]
   (when-some [ctx *context*]
     (let [client   (:client ctx)
           sessions (get-in ctx [:server :sessions])]
       (swap! @sessions [client :resources] assoc id resource-cleanup-fn)
       id))))

(defn cleanup-resource!
  "Cleans up the resource with the specified `id`, if it exists."
  [id]
  (when-some [ctx *context*]
    (let [client   (:client ctx)
          sessions (get-in ctx [:server :sessions])
          cleanup  (get-in @sessions [client :resources id])]
      (try
        (when cleanup
          (cleanup))
        (finally
          (swap! sessions update-in [client :resources] dissoc id))))))

(defn cleanup-resources!
  "Cleans up all resources"
  []
  (when-some [ctx *context*]
    (let [client    (:client ctx)
          sessions  (get-in ctx [:server :sessions])
          resources (get-in @sessions [client :resources])]
      (try
        (doseq [cleanup-fn (vals resources)]
          (cleanup-fn))
        (finally
          (swap! sessions update client assoc :resources {}))))))

(defmacro defhandler
  "Define a plasma handler"
  {:arglists '([name doc-str? attr-map? [params*] body])}
  [& args]
  (let [{:keys [name attr-map bindings body]} (macro/parse-args args)
        ns-name                               (some-> &env :ns :name str)
        cljs?                                 (some? ns-name)
        new-bindings                          (if-not cljs?
                                                bindings
                                                (mapv (fn [_] (gensym)) bindings))
        attr-map                              (if (and cljs? (not (:arglists attr-map)))
                                                (-> attr-map
                                                    (assoc :arglists `([~@bindings])))
                                                attr-map)
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
           ~new-bindings
           ~@body))))

(defmacro defstream
  "Define a plasma stream"
  {:arglists '([name doc-str? attr-map? [params*] body])}
  [& args]
  (let [{:keys [name attr-map bindings body]} (macro/parse-args args)
        ns-name                               (some-> &env :ns :name str)
        cljs?                                 (some? ns-name)
        new-bindings                          (if-not cljs?
                                                bindings
                                                (mapv (fn [_] (gensym)) bindings))
        attr-map                              (if (and cljs? (not (:arglists attr-map)))
                                                (-> attr-map
                                                    (assoc :arglists `([~@bindings])))
                                                attr-map)
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
           ~new-bindings
           ~@body))))
