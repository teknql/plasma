(ns plasma.macro
  "Macro helpers for plasma"
  {:no-doc true})

(defn parse-args
  "Helper for writing macros. Returns the data as a structured map."
  [args]
  (let [[name args]       [(first args) (rest args)]
        [doc-str args]    (if (string? (first args))
                            [(first args) (rest args)]
                            [nil args])
        [attr-map args]   (if (map? (first args))
                            [(first args) (rest args)]
                            [nil args])
        [bindings & body] args
        attr-map          (cond-> (or attr-map {})
                            doc-str (assoc :doc doc-str))]
    {:name     name
     :attr-map attr-map
     :bindings bindings
     :body     body}))
