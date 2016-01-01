(ns ^:figwheel-no-load figwheel.client.eval
  (:require [clojure.string :as string]
            [goog.userAgent.product :as product]
            [figwheel.client.socket :as socket]
            [figwheel.client.utils :as utils])
    (:import [goog]))

(defn figwheel-repl-print [args]
  (socket/send! {:figwheel-event "callback"
                 :callback-name "figwheel-repl-print"
                 :content args})
  args)

(defn console-print [args]
  (.apply (.-log js/console) js/console (into-array args))
  args)

(defn repl-print-fn [& args]
  (-> args
      console-print
      figwheel-repl-print)
  nil)

(defn enable-repl-print! []
  (set! *print-newline* false)
  (set! *print-fn* repl-print-fn))

(defn async-eval-fn? [eval-fn]
  (boolean (:async (meta eval-fn))))

(defn eval-helper
  "Evaluates javascript code in way specified by figwheel config (opts).
   If there is custom eval-fn defined use it, otherwise call javascript eval on passed code.
   Custom eval-fn can be marked as async. This can be used by Dirac DevTools."
  ([code opts]
   (eval-helper code opts identity))
  ([code opts result-callback]
    (let [{:keys [eval-fn]} opts]
      (if eval-fn
        (if (async-eval-fn? eval-fn)
          (eval-fn code opts result-callback)
          (result-callback (eval-fn code opts)))
        (result-callback (js* "eval(~{code})"))))))

(defn truncate-stack-trace [stack-str]
  (take-while #(not (re-matches #".*eval_javascript_STAR__STAR_.*" %))
              (string/split-lines stack-str)))

(defn get-ua-product []
  (cond
    (utils/node-env?) :chrome
    product/SAFARI :safari
    product/CHROME :chrome
    product/FIREFOX :firefox
    product/IE :ie))

(defn make-success-eval-report [result]
  {:status     :success
   :ua-product (get-ua-product)
   :value      result})

(defn make-exception-eval-report [exception]
  {:status     :exception
   :ua-product (get-ua-product)
   :value      exception})

(defn eval-javascript [code opts result-handler]
  (let [result-callback (fn [result full-report?]
                          (result-handler
                            (if full-report?
                              result
                              (make-success-eval-report result))))]
    (try
      (eval-helper code opts result-callback)
      (catch js/Error e
        (result-handler
          (assoc (make-exception-eval-report e)
            :stacktrace (string/join "\n" (truncate-stack-trace (.-stack e)))
            :base-path utils/base-path)))
      (catch :default e
        (result-handler
          (assoc (make-exception-eval-report e)
            :stacktrace "No stacktrace available."))))))

(defn stringify-eval-report [eval-report]
  (let [{:keys [status]} eval-report]
    (case status
      :exception (update eval-report :value pr-str)
      eval-report)))

(defn repl-eval-answer [callback-name eval-report]
  (socket/send! {:figwheel-event "callback"
                 :callback-name  callback-name
                 :content        (stringify-eval-report eval-report)}))

(defn ensure-cljs-user
  "The REPL can disconnect and reconnect lets ensure cljs.user exists at least."
  []
  ;; this should be included in the REPL
  (when-not js/cljs.user
    (set! js/cljs.user #js {})))

(defn repl-eval-javascript [code opts callback-name]
  (ensure-cljs-user)
  (binding [*print-fn* repl-print-fn
            *print-newline* false]
    (eval-javascript code opts (partial repl-eval-answer callback-name))))