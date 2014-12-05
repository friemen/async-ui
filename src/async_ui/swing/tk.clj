(ns async-ui.swing.tk
  (:require [async-ui.core :as v]
            [async-ui.swing.binding :as b]
            [async-ui.swing.builder :refer [build]])
  (:import [async_ui.core Toolkit]
           [java.awt Color Container]
           [javax.swing JComponent JFrame JPanel JScrollPane SwingUtilities]))


(defrecord SwingToolkit []
  Toolkit
  (run-now [tk f]
    (SwingUtilities/invokeAndWait f))
  (show-view! [tk view]
    (some-> view :vc (.setVisible true)))
  (hide-view! [tk view]
    (some-> view :vc (.setVisible false)))
  (build-vc-tree [tk view]
    (v/hide-view! tk view)
    (assoc view :vc (build (:spec view))))
  (bind-vc-tree! [tk view]
    (b/bind! (:vc view) (:events view))
    (assoc view
      :setter-fns (v/setter-map tk (:vc view) b/setter-fns (:mapping view))))
  (vc-children [tk vc]
    (map #(if (instance? JScrollPane %)
            (-> % .getViewport .getView)
            %)
         (condp = (class vc)
           JFrame       [(.getContentPane vc)]
           JPanel       (.getComponents vc)
           [])))
  (vc-name [tk vc]
    (.getName vc))
  (set-vc-error! [tk vc msgs]
    (when (instance? JComponent vc)
      (if (seq msgs)
        (doto vc
          (if-not (.getClientProperty vc :background-color)
            (.putClientProperty vc :background-color (.getBackground vc)))
          (.setBackground Color/RED)
          (.setToolTipText (apply str msgs)))
        (if-let [bgc (.getClientProperty vc :background-color)]
          (doto vc
            (.setBackground bgc)
            (.setToolTipText nil)))))))

(defn make-toolkit
  []
  (SwingToolkit.))
