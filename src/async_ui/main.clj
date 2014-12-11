(ns async-ui.main
  (:require [async-ui.ex-master-detail :as ex])
  (:gen-class))

(defn -main
  [& args]
  (ex/start!)
  ;; make sure the JVM does not terminate before window is shown
  (Thread/sleep 500))
