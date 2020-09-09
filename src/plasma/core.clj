(ns plasma.core
  "Core namespace for Plasma"
  (:require [plasma.macro :as macro]))

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
  "Registers a resource in the client resources with the given `id` and returns the `id`.

  Calls `resource-cleanup-fn` on close.

  If no `id` is provided, one will be generated automatically."
  ([resource-cleanup-fn]
   (register-resource! (java.util.UUID/randomUUID) resource-cleanup-fn))
  ([id resource-cleanup-fn]
   (when *resources*
     (swap! *resources* assoc id resource-cleanup-fn)
     id)))

(defn cleanup-resource!
  "Cleans up the resource with the specified `id`, if it exists."
  [id]
  (when-some [cleanup-fn (some-> *resources* deref (get id))]
    (try
      (cleanup-fn)
      (finally
        (swap! *resources* dissoc id)))))

(defmacro defhandler
  "Define a plasma handler"
  {:arglists '([name doc-str? attr-map? [params*] body])}
  [& args]
  (let [{:keys [name attr-map bindings body]} (macro/parse-args args)
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
  (let [{:keys [name attr-map bindings body]} (macro/parse-args args)
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
