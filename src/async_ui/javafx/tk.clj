(ns async-ui.javafx.tk
  (:require [async-ui.core :as v]
            [async-ui.javafx.utils :refer [launch-if-necessary]]
            [async-ui.javafx.builder :refer [build]]
            [async-ui.javafx.binding :as b])
  (:import [async_ui.core Toolkit]
           [javafx.application Platform]
           [javafx.scene.control Tooltip]
           [javafx.stage Stage]
           [org.tbee.javafx.scene.layout MigPane]))


(defrecord JfxToolkit []
  Toolkit
  (run-now [tk f]
    (launch-if-necessary)
    (let [result (promise)]
      (Platform/runLater #(deliver result 
                                   (try (f)
                                        (catch Exception e (do (.printStackTrace e) e)))))
    @result))
  (show-view! [tk view]
    (some-> view :vc .show))
  (hide-view! [tk view]
    (some-> view :vc .close))
  (build-vc-tree [tk view]
    (v/hide-view! tk view)
    (assoc view :vc (build (:spec view))))
  (bind-vc-tree! [tk view]
    (b/bind! (:vc view) (:events view))
    (assoc view
      :setter-fns (v/setter-map tk (:vc view) b/setter-fns (:mapping view))))
  (vc-name [tk vc]
    (if (instance? Stage vc)
      (-> vc .getScene .getRoot .getProperties (.get :window-id))
      (.getId vc)))
  (vc-children [tk vc]
    (condp instance? vc
      MigPane (or (.getChildren vc) [])
      Stage (-> vc .getScene .getRoot vector)
      []))
  (set-vc-error! [tk vc msgs]
    (if (seq msgs)
      (.setTooltip vc (Tooltip. (apply str msgs)))
      (.setTooltip vc nil))))


(defn make-toolkit
  []
  (JfxToolkit.))
