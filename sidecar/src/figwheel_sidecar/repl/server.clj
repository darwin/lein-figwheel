(ns figwheel-sidecar.repl.server
  (:require [org.httpkit.server :refer [run-server with-channel on-close on-receive send! open?]]
            [clojure.core.async :refer [chan <!! <! put! alts!! timeout close! go go-loop]]
            [cognitect.transit :as transit]
            [figwheel-sidecar.utils :as utils])
  (:import (java.io ByteArrayInputStream ByteArrayOutputStream)))

(def clients (atom {}))

; -- clients data manipulation ----------------------------------------------------------------------------------------------

(defn make-client [id wschannel]
  {:id        id
   :wschannel wschannel})

(defn add-client! [client]
  (println "add client" client)
  (swap! clients assoc (:id client) client))

(defn remove-client! [client]
  (println "remove client" client)
  (swap! clients dissoc (:id client)))

(defn get-client [client-id]
  (get @clients (str client-id)))

; -- message processing -----------------------------------------------------------------------------------------------------

(defn send-message-to-client! [client msg]
  {:pre [client]}
  (let [{:keys [wschannel]} client]
    (assert wschannel (str "No websocket channel? " (pr-str client)))
    (if (open? wschannel)
      (let [serialized-msg (utils/serialize-msg msg)]
        (go
          (<! (timeout 1000))
          (send! wschannel serialized-msg)))
      (println "socket already closed?"))))

(defn send-message-to-client-id! [client-id msg]
  (if-let [client (get-client client-id)]
    (send-message-to-client! client msg)
    (println "no client?" client-id)))

(defn ask-client-to-deliver-figwheel-message! [client-id figwheel-msg]
  (send-message-to-client-id! client-id {:command          "deliver-figwheel-message"
                                         :figwheel-message (utils/serialize-msg figwheel-msg)}))

(defn read-msg [data]
  (try
    (utils/unserialize-msg data)
    (catch Exception e
      (println "Figwheel REPL server: message from client couldn't be read!" e))))

(defn handle-client-msg [server-state client data]
  (let [msg (read-msg data)]
    (case (:command msg)
      ; TODO: messages
      (println "Figwheel REPL server: received unrecognized message" msg))))

; -- client connections -----------------------------------------------------------------------------------------------------

(defn open-client-connection [server-state params wschannel]
  (let [client-id (str (:id params))
        client (make-client client-id wschannel)]
    (println "repl client connected" client)
    (add-client! client)
    (on-close wschannel (fn [status]
                          (println "Figwheel REPL server: client disconnected " status)
                          (remove-client! client)))
    (on-receive wschannel (fn [data]
                            (handle-client-msg server-state client data)))))

(defn new-client-connection-handler [server-state]
  (fn [request]
    (with-channel request channel
      (open-client-connection server-state (:params request) channel))))