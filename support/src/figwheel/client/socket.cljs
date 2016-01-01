(ns figwheel.client.socket
  (:require
    [figwheel.client.utils :as utils]
    [cognitect.transit :as transit]))

;; messages have the following formats

;; files-changed message
;; { :msg-name :files-changed
;;   :files    [{:file "/js/compiled/out/example/core.js",
;;               :type :javascript,
;;               :msg-name :file-changed,
;;               :namespace "example.core" }] }

;; css-files-changed message
;; there should really only be one file in here at a time
;; { :msg-name :css-files-changed
;;   :files    [{:file "/css/example.css",
;;               :type :css }] }

;; compile-failed message
;; { :msg-name :compile-failed
;;   :exception-data {:cause { ... lots of exception info ... } }}
;; the exception data is nested raw info obtained for the compile time
;; exception

(declare open)

(defonce message-history-atom (atom (list)))

(defonce socket-atom (atom false))
(defonce socket-reader (transit/reader :json))
(defonce socket-writer (transit/writer :json))

(defn serialize-message [msg]
  (transit/write socket-writer msg))

(defn unserialize-message [serialized-msg]
  (transit/read socket-reader serialized-msg))

(defn connected? []
  (boolean @socket-atom))

(defn send! [msg]
  (when (connected?)
    (utils/debug-prn (pr-str msg))
    (.send @socket-atom (serialize-message msg))))

(defn clear-socket-atom! []
  (reset! socket-atom false))

(defn close! []
  (set! (.-onclose @socket-atom) identity)
  (let [socket @socket-atom]
    (clear-socket-atom!)
    (.close socket)))

(defn deliver-message! [serialized-msg]
  (try
    (if-let [msg (unserialize-message serialized-msg)]
      (do
        (utils/debug-prn msg)
        (if (and (map? msg) (:msg-name msg) (not= (:msg-name msg) :ping))                                                     ; don't forward pings
          (do
            (.log js/console "DELIVER" msg)
            (swap! message-history-atom conj msg))))
      (utils/log :warn (str "unable to unserialize message:" serialized-msg)))
    (catch :default ex
      (.error js/console "unable to unserialize message" ex))))

(defn open-connection! [socket]
  (reset! socket-atom socket)
  (when (utils/html-env?)
    (.addEventListener js/window "beforeunload" close!))
  (utils/log :debug "Figwheel: socket connection established"))

(defn close-connection! [opts]
  (clear-socket-atom!)                                                                                                        ; socket close can be triggered by someone external, not only via close!
  (let [{:keys [retry-count retried-count]} opts
        retried-count (or retried-count 0)]
    (utils/debug-prn "Figwheel: socket closed or failed to open")
    (if (> retry-count retried-count)
      (let [next-timeout (min 10000 (+ 2000 (* 500 retried-count)))                                                           ; linear back off
            new-opts (update opts :retried-count inc)]
        (js/setTimeout (partial open new-opts) next-timeout)))))

(defn report-connection-error! []
  (utils/debug-prn "Figwheel: socket error"))

(defn open [opts]
  (let [{:keys [websocket-url build-id]} opts]
    (if-let [WebSocket (utils/get-websocket-imp)]
      (let [url (str websocket-url (if build-id (str "/" build-id) ""))]
        (utils/log :debug (str "Figwheel: trying to open cljs reload socket: " url))
        (let [socket (WebSocket. url)]
          (set! (.-onmessage socket) #(deliver-message! (.-data %)))
          (set! (.-onopen socket) #(open-connection! socket))
          (set! (.-onclose socket) #(close-connection! opts))
          (set! (.-onerror socket) #(report-connection-error!))
          socket))
      (utils/log :debug (if (utils/node-env?)
                          "Figwheel: Can't start Figwheel!! Please make sure ws is installed\n do -> 'npm install ws'"
                          "Figwheel: Can't start Figwheel!! This browser doesn't support WebSockets")))))