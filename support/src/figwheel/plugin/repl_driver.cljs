(ns figwheel.plugin.repl-driver
  (:require
    [figwheel.client.eval :as eval]
    [figwheel.client.socket :as socket]))

(def subscribers (atom []))

; unfortunately we don't have an easy way how to pass opts into our exported functions
; right now, we assume there will be only one repl-driver in given figwheel instance
(def figwheel-opts (volatile! {}))

; -- subscribers ------------------------------------------------------------------------------------------------------------

(defn has-subscriber? [state subscriber]
  (boolean (some #(= subscriber %) state)))

(defn add-subscriber [state subscriber]
  (assert (not (has-subscriber? state subscriber)))
  (conj state subscriber))

(defn remove-subscriber [state subscriber]
  (assert (has-subscriber? state subscriber))
  (remove #(= subscriber %) state))

; -- handler for incoming server messages -----------------------------------------------------------------------------------

(defn handle-message [msg opts]
  (doseq [subscriber @subscribers]
    (subscriber msg opts)))

; -- low-level sending ------------------------------------------------------------------------------------------------------

(defn send-message! [command & [opts]]
  (socket/send! (merge opts
                       {:figwheel-event "repl-driver"
                        :command        command})))

(defn set-opts! [opts]
  (vreset! figwheel-opts opts))

; -- API available for client use -------------------------------------------------------------------------------------------

(defn ^:export subscribe [subscriber-callback]
  (swap! subscribers add-subscriber subscriber-callback))

(defn ^:export unsubscribe [subscriber-callback]
  (swap! subscribers remove-subscriber subscriber-callback))

(defn ^:export is-socket-connected []
  (socket/connected?))

(defn ^:export is-repl-available []
  true)                                                                                                                       ; TODO: detect repl availablity somehow

(defn ^:export eval [request-id code & [user-input]]
  (send-message! "eval" {:request-id request-id
                         :code       code
                         :input      (or user-input code)}))

(defn ^:export eval-js [code callback-name]
  (eval/repl-eval-javascript code @figwheel-opts callback-name))

(defn ^:export request-ns []
  (send-message! "request-ns"))