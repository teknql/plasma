(ns plasma.uix
  #?(:cljs (:require-macros [plasma.uix]))
  (:require [uix.hooks.alpha :as hooks]
            [plasma.uix.hot-reload :as hot-reload]
            [uix.core.alpha :as uix]
            #?@(:cljs [[promesa.core :as p]
                       [plasma.client.stream :as s]])))

(defmacro state
  "Hot reload aware state for "
  [val]
  (if-not (hot-reload/enabled? &env)
    `(hooks/state ~val)
    (let [hot-reload-id (hot-reload/get-identifier! &env &form)]
      `(let [val# (if-let [known-state# (get @hot-reload/cache ~hot-reload-id)]
                    @known-state#
                    ~val)
             res# (hooks/state val#)]
         (swap! hot-reload/cache assoc ~hot-reload-id res#)
         res#))))

(defmacro use-rpc
  "Macro helper for RPC calls using Plasma in UIX"
  [rpc-call & [deps]]
  (if-not (:ns &env)
    `{:data ~rpc-call :loading? false}
    `(let [[state#] (state {:loading? false})]
       (hooks/effect! (fn []
                        (reset! state!# {:loading? true})
                        (-> ~rpc-call
                            (p/chain #(reset! state!# {:loading? false :data %}))
                            (p/catch #(reset! state!# {:loading? false :error %})))
                        js/undefined)
                      ~deps)
       state#)))

(defmacro with-stream
  "Macro to consume a stream"
  [deps init f]
  `(uix/with-effect ~deps
     (let [s# ~init]
       (plasma.client.stream/consume-via! s# #(do (~f %)
                                                  true))
       #(plasma.client.stream/close! s#))))

(defmacro with-rpc
  "Macro helper for making an RPC call"
  ([deps rpc on-success]
   `(with-rpc ~deps ~rpc ~on-success nil))
  ([deps rpc on-success on-fail]
   (let [cljs? (:ns &env)]
     (if-not cljs?
       `nil
       `(let [on-success# ~on-success
              on-fail#    ~on-fail]
          (uix/with-effect ~deps
            (cond-> ~rpc
              on-success# (promesa.core/then on-success#)
              on-fail#    (promesa.core/catch on-fail#))))))))
