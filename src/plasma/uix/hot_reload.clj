(ns plasma.uix.hot-reload)

(defonce generation (atom 0))
(defonce statement-registry (atom {}))

(defn enabled?
  "Macro helper fn to return whether hot reload is enabled in the provided `&env`"
  [&env]
  (= :dev (:shadow.build/mode &env)))

(defn- env->scope
  "Return the scope as a symbol for the provided `&env`"
  [&env]
  (let [fn-name (-> &env :fn-scope last :name)
        ns-name (-> &env :ns :name)]
    (symbol (str ns-name) (str fn-name))))

(def compiler-ctx
  "State atom for the actively compiled scope"
  (atom nil))

(defn reset-ctx!
  "Resets all compilation contexts"
  [scope-name]
  (reset! compiler-ctx {:name       scope-name
                        :statements []}))

(defn cleanup-registry!
  "Purges outdated entries from the compiler registry."
  []
  (let [gen @generation]
    (swap! statement-registry
           (fn [reg]
             (->> reg
                  (filter (comp #(= gen %) :generation val))
                  (into {}))))))

(defn compile-prepare!
  "Build hook for shadow cljs"
  {:shadow.build/stage :compile-prepare}
  [build-state]
  (swap! generation inc)
  build-state)

(defn compile-finish!
  "Build hook for shadow cljs"
  {:shadow.build/stage :compile-finish}
  [build-state]
  (cleanup-registry!)
  (reset-ctx! nil)
  build-state)

(defn- new-scope?
  "Return whether we have arrived at a different scope than our current one."
  [scope-name]
  (not= scope-name (:name @compiler-ctx)))

(defn register-statement!
  "Register the provided statement in the context. Return the new statement context ID"
  [form]
  (let [new-ctx (swap! compiler-ctx update :statements conj form)
        k       [(:name new-ctx) (:statements new-ctx)]
        id      (get-in @statement-registry [k :id] (str (java.util.UUID/randomUUID)))]
    (swap! statement-registry assoc k {:id id :generation @generation})
    id))

(defn get-identifier!
  "Takes an environment and a form and returns a string identifier that will be used
  to persist the effect / state through reloads."
  [env form]
  (let [scope (env->scope env)]
    (when (new-scope? scope)
      (reset-ctx! scope))
    (register-statement! form)))
