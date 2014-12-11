(ns async-ui.javafx.utils
  (:require [async-ui.javafx.application :as app]
            [environ.core :refer [env]])
  (:import [javafx.application Application Platform]))


(defonce ^:private app-starter-thread (atom nil))
(defonce ^:private force-toolkit-init
  (do
    ;; Keep JavaFX running even if no window is visible
    (Platform/setImplicitExit false)
    ;; This is a hack to finally call 
    ;; com.sun.javafx.application.PlatformImpl.startup()
    ;; which starts the JavaFX application thread
    ;; this is needed because static class initializers in
    ;; JavaFX classes rely on calls that expect to be run on
    ;; the app thread, sigh
    (javafx.embed.swing.JFXPanel.)))

(compile 'async-ui.javafx.application) 


(defn launch-if-necessary
  []
  (when-not @app-starter-thread
    (reset! app-starter-thread (Thread. #(javafx.application.Application/launch
                                          async_ui.javafx.application
                                          (into-array String []))))
    (.start @app-starter-thread))
  @app/root-stage)


(defn root-window
  []
  (launch-if-necessary))


;; Ensure that JavaFX is shutdown when in uberjar compilation.
;; However, it still takes about 1 minute before the JVM actually terminates.

;; To enable this add the entry :env {:javafx-exit true} in :uberjar profile.
;; Please note that the values are "transmitted" to the compile process
;; via a .lein-env file.
;; Thus, if you do an uberjar and then execute the resulting jar from the
;; project dir the JavaFX platform exit will happen again.
;; lein run or repl are not affected because they overwrite .lein-env

(when (env :javafx-exit)
  (future (println "Process" (-> (java.lang.management.ManagementFactory/getRuntimeMXBean) .getName))
          (println "Waiting 5 secs before exiting JavaFX platform")
          (Thread/sleep 5000)
          (println "Exiting JavaFX platform")
          (Platform/setImplicitExit true)
          (Platform/exit)))
