(ns async-ui.javafx.builder
  "JavaFX UI builder"
  (:require [async-ui.forml :as f]
            [async-ui.core]
            [async-ui.javafx.utils :refer [root-window]]
            [metam.core :refer [metatype metatype?]])
  (:import [javafx.scene.control Button CheckBox ChoiceBox Label ListView
                                 RadioButton TableView TableColumn TextField
                                 ToggleGroup]
           [javafx.scene Scene]
           [javafx.stage Stage Modality]
           [javafx.util Callback]
           [javafx.beans.property ReadOnlyObjectWrapper]
           [org.tbee.javafx.scene.layout MigPane]))


;; builder stuff

(declare build add-component!)

(defn- make
  [clazz spec]
  (doto (.newInstance clazz)
    (.setId (:name spec))))

(defn add-component!
  [owner spec]
  (let [c (build spec)
        lyhint (:lyhint spec)]
    (.add owner c lyhint)
    c))


(defn- make-panel
  [spec]
  (let [p (MigPane. (:lygeneral spec) 
                    (:lycolumns spec)
                    (:lyrows spec))]
	  (.setId p (:name spec))
    (doseq [spec (:components spec)]
      (add-component! p spec))
    p))


(defmulti build metatype
  :hierarchy #'f/forml-hierarchy)


(defmethod build :default
  [spec]
  (throw (IllegalArgumentException. (str "Cannot build type '" (metatype spec) "'"))))


(defmethod build ::f/button
  [spec]
  (doto (make Button spec)
    (.setText (:text spec))))


(defmethod build ::f/label
  [spec]
  (doto (make Label spec)
    (.setText (:text spec))))


(defmethod build ::f/listbox
  [spec]
  (make ListView spec))


(defmethod build ::f/panel
  [spec]
  (make-panel spec))


(defmethod build ::f/textfield
  [spec]
  (make TextField spec))


(defn- make-stage
  "Returns either the root stage if it wasn't already initialized with a scene,
  or a new stage that is owned by the root stage, or an explicitly specified
  owner."
  [spec]
  (let [root-stage (root-window)]
    (if (-> root-stage .getScene)
      (doto (Stage.)
        (.initOwner (if-let [owner (some-> async-ui.core/views
                                           deref
                                           (get (:owner spec))
                                           :vc)]
                      owner
                      root-stage))
        (.initModality (case (:modality spec)
                         :window Modality/WINDOW_MODAL
                         :application Modality/APPLICATION_MODAL
                         Modality/NONE)))
      root-stage)))


(defmethod build ::f/window
  [spec]
  (let [root (build (:content spec))
        scene (Scene. root)]
    (-> root .getProperties (.put :window-id (:name spec)))
    (doto (make-stage spec)
      (.setScene scene)
      (.show)
      (.sizeToScene)
      (.setTitle (:title spec)))))

