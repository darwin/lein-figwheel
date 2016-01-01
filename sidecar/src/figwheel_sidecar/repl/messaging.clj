(ns figwheel-sidecar.repl.messaging
  (:require
    [clojure.core.async :refer [chan <!! put! alts!! timeout close! go go-loop]]
    [figwheel-sidecar.protocols :as protocols]
    [figwheel-sidecar.repl.server :as server]))

(def message-timeout-value
  {:status     :exception
   :value      "Eval timed out!"
   :stacktrace "No stacktrace available."})

(defn send-message [figwheel-server command & [payload callback]]
  (let [driver-payload {:msg-name :repl-driver
                        :command  command}
        effective-payload (merge payload driver-payload)]
    (protocols/-send-message figwheel-server (:build-id figwheel-server) effective-payload callback)))

(defn send-message-with-answer [figwheel-server command payload]
  (let [result-chan (chan)
        timeout-chan (timeout 8000)
        receiver (fn [result] (put! result-chan result))]
    (send-message figwheel-server command payload receiver)
    (let [[value] (alts!! [result-chan timeout-chan])]
      (or value message-timeout-value))))                                                                                     ; note: timeout-chan just closes with nil value

; -- messages ---------------------------------------------------------------------------------------------------------------

(defn announce-job-start [figwheel-server request-id]
  (send-message figwheel-server :job-start {:request-id request-id}))

(defn announce-job-end [figwheel-server request-id]
  (send-message figwheel-server :job-end {:request-id request-id}))

(defn announce-repl-ns [figwheel-server ns-as-string]
  {:pre [(string? ns-as-string)]}
  (send-message figwheel-server :repl-ns {:ns ns-as-string}))

(defn report-output [figwheel-server request-id kind content]
  (send-message figwheel-server :output {:request-id request-id
                                         :kind       kind
                                         :content    content}))

(defn eval-js [figwheel-server request-id js-code]
  {:pre [(string? js-code)]}
  (let [payload {:msg-name   :repl-driver
                 :command    :eval-js
                 :request-id request-id
                 :code       js-code}]
    (server/ask-client-to-deliver-figwheel-message! request-id payload)
    (send-message-with-answer figwheel-server :eval-js payload)))