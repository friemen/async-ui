(ns async-ui.swing.builder
  "Swing UI builder"
  (:require [async-ui.forml :as f]
            [metam.core :refer [metatype metatype?]])
  (:import [javax.swing JButton JFrame JLabel JList JPanel JTextField]
           [javax.swing DefaultListModel]
           [java.awt.event ActionListener]
           [net.miginfocom.swing MigLayout]))


(defn- make
  [clazz spec]
  (doto (.newInstance clazz)
    (.setName (:name spec))))


(defmulti build metatype
  :hierarchy #'f/forml-hierarchy)

(defmethod build :default
  [spec]
  (throw (IllegalArgumentException. (str "Cannot build type '" (metatype spec) "'"))))

(defmethod build ::f/button
  [spec]
  (doto (make JButton spec)
    (.setText (:text spec))))

(defmethod build ::f/label
  [spec]
  (doto (make JLabel spec)
    (.setText (:text spec))))

(defmethod build ::f/listbox
  [spec]
  (doto (make JList spec)
    (.setModel (DefaultListModel.))))

(defmethod build ::f/panel
  [spec]
  (let [p (doto (make JPanel spec)
            (.setLayout (MigLayout. (:lygeneral spec) (:lycols spec) (:lyrows spec))))]
    (doseq [child (:components spec)]
      (.add p (build child) (:lyhint child)))
    p))

(defmethod build ::f/textfield
  [spec]
  (make JTextField spec))

(defmethod build ::f/window
  [spec]
  (doto (make JFrame spec)
    (.setTitle (:title spec))
    (.setContentPane (-> spec :content build))
    (.setVisible true)
    (.setDefaultCloseOperation JFrame/DISPOSE_ON_CLOSE)
    (.pack)))
