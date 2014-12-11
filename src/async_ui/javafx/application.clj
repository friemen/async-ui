(ns async-ui.javafx.application
  "JavaFX Startup utilities"
  (:import [javafx.application Application])
  (:gen-class
   :extends javafx.application.Application))

(defonce root-stage (promise))

(defn -start
  [this stage]
  (deliver root-stage stage))
