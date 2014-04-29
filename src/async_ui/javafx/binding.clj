(ns async-ui.javafx.binding
  (:require [clojure.core.async :refer [go >!]]
            [async-ui.core :refer [make-event]])
  (:import [javafx.beans.value ChangeListener]
           [javafx.collections FXCollections ListChangeListener]
           [javafx.event EventHandler]
           [javafx.scene.control Button ListView TextField]
           [javafx.stage Stage]
           [org.tbee.javafx.scene.layout MigPane]))


(defmacro without-listener
  "Removes the listener defined by k (and expected in the properties of a JavaFX Node)
  from the thing reachable via vc and the access-path, then executes the expr and adds
  the listener afterwards."
  [vc access-path k expr]
  `(let [l# (-> ~vc .getProperties (.get ~k))]
     (-> ~vc ~@access-path (.removeListener l#))
     ~expr
     (-> ~vc ~@access-path (.addListener l#))))


; ------------------------------------------------------------------------------
;; Create setter functions to uniformly update properties in visual components

(defn- common-setter-fns
  [vc]
  {:enabled #(.setDisable vc (not %))
   :visible #(.setVisible vc %)})


(defmulti setter-fns
  "Returns a map from keyword to 1-arg function for the given visual component.
  Each function is used to update a property of the visual component with the 
  given formatted value."
  class)

(defmethod setter-fns :default
  [vc]
  {})

(defmethod setter-fns Button
  [vc]
  (assoc (common-setter-fns vc)
    :text #(.setText vc %)))

(defmethod setter-fns ListView
  [vc]
  (assoc (common-setter-fns vc)
    :selection #(without-listener vc [.getSelectionModel .getSelectedIndices] :selection-listener
                                  (if (seq %)
                                    (-> vc .getSelectionModel (.selectIndices (first %) (int-array (rest %))))
                                    (-> vc .getSelectionModel (.selectIndices -1 (int-array [])))))
    :items #(without-listener vc [.getSelectionModel .getSelectedIndices] :selection-listener
                              (.setItems vc (FXCollections/observableArrayList %)))))

(defmethod setter-fns Stage
  [vc]
  {:title #(.setTitle vc %)})

(defmethod setter-fns TextField
  [vc]
  (assoc (common-setter-fns vc)
    :text #(without-listener vc [.textProperty] :text-listener
                             (let [p (-> vc .getCaretPosition)]
                               (doto vc
                                 (.setText %)
                                 (.positionCaret (min p (count %))))))))

; ------------------------------------------------------------------------------
;; Connect callbacks that write to the events channel of the view

(defmulti bind!
  "Binds listeners to all components of the visual component tree vc.
  Each listener puts an event onto the channel."
  (fn [vc events-chan]
    (class vc)))

(defmethod bind! :default
  [vc events-chan]
  nil)

(defmethod bind! Button
  [vc events-chan]
  (.setOnAction vc
                (reify EventHandler
                  (handle [_ evt]  
                    (go (>! events-chan (make-event (.getId vc) :action)))))))

(defmethod bind! ListView
  [vc events-chan]
  (let [l (reify ListChangeListener
            (onChanged [_ _]
              (go (>! events-chan (make-event (.getId vc)
                                              :selection
                                              :update
                                              (-> vc .getSelectionModel .getSelectedIndices vec))))))]
    (-> vc .getSelectionModel .getSelectedIndices (.addListener l))
    (-> vc .getProperties (.put :selection-listener l))))

(defmethod bind! MigPane
  [vc events-chan]
  (doseq [child (.getChildren vc)]
    (bind! child events-chan)))

(defmethod bind! Stage
  [vc events-chan]
  (bind! (-> vc .getScene .getRoot) events-chan)
  (let [window-id (-> vc .getScene .getRoot .getProperties (.get :window-id))]
    (-> vc (.setOnCloseRequest (reify EventHandler
                                 (handle [_ evt]
                                   (go (>! events-chan (make-event window-id :close)))))))))

(defmethod bind! TextField
  [vc events-chan]
  (let [l (reify ChangeListener
            (changed [_ prop ov nv]
              (go (>! events-chan (make-event (.getId vc) :text :update nv)))))]
    (-> vc .textProperty (.addListener l))
    (-> vc .getProperties (.put :text-listener l))))
