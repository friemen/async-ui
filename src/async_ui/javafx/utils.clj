(ns async-ui.javafx.utils
  (:require [async-ui.javafx.application :as app])
  (:import [javafx.application Application Platform]))


(defonce ^:private app-starter-thread (atom nil))
(defonce ^:private force-toolkit-init
  (do 
    (Platform/setImplicitExit false)
    (javafx.embed.swing.JFXPanel.)))


(compile 'async-ui.javafx.application)

(defn launch-if-necessary
  []
  (when-not @app-starter-thread
    (reset! app-starter-thread (Thread. #(Application/launch
                                          async_ui.javafx.application
                                          (into-array String []))))
    (.start @app-starter-thread))
  @app/root-stage)


(defn root-window
  []
  (launch-if-necessary))

