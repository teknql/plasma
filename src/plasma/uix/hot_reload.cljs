(ns plasma.uix.hot-reload
  "Namespace facilitating hot reload for plasma"
  (:require [uix.hooks.alpha :as hooks])
  (:require-macros [plasma.uix.hot-reload]))

(defonce cache (atom {}))

(defn purge-cache!
  "Resets the cache"
  []
  (reset! cache {}))
