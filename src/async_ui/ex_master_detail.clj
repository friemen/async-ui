(ns async-ui.ex-master-detail
  (:require [clojure.core.async :refer [go <! >!] :as async]
            [examine.core :as e]
            [examine.constraints :as c]
            [async-ui.forml :refer :all]
            [async-ui.core :as v]
            [async-ui.javafx.tk :as javafx]
            [async-ui.swing.tk :as swing]))


; ----------------------------------------------------------------------------
;; TODOs
;; - Demonstrate testing support with event recording and play-back
;; - Validation message display
;; - Modality between windows

; ----------------------------------------------------------------------------
;; In the REPL:
;; Compile this namespace.

;; Run this snippet with JavaFX
#_ (do (ns async-ui.ex-master-detail) (start!))


;; To just start the toolkit process with JavaFX
#_(do
  (ns async-ui.ex-master-detail)
  (def javafx-tk (javafx/make-toolkit))
  (v/run-tk javafx-tk))

;; Start only process for master view
#_(v/run-view #'item-manager-view
              #'item-manager-handler
              {:item ""
               :items ["Foo" "Bar" "Baz"]})

;; We could start the process for the details view directly
#_(v/run-view #'item-editor-view
              #'item-editor-handler
              {:text "Foo"})

;; Terminate Toolkit process
(v/stop-tk)


; ----------------------------------------------------------------------------
;; A Detail View

(defn item-editor-view
  [data]
  (-> (v/make-view "item-editor"
                   (window "Item Editor"
                           :content
                           (panel "Content" :lygeneral "wrap 2, fill" :lycolumns "[|100,grow]" 
                                  :components
                                  [(label "Text") (textfield "text" :lyhint "growx")
                                   (panel "Actions" :lygeneral "ins 0" :lyhint "span, right"
                                          :components
                                          [(button "OK") (button "Cancel")])])))
      (assoc :mapping (v/make-mapping :text ["text" :text])
             :validation-rule-set (e/rule-set :text (c/min-length 1))
             :data data)))

(defn item-editor-handler
  [view event]
  (go (case ((juxt :source :type) event)
        ["OK" :action]
        (assoc view :terminated true)
        
        ["Cancel" :action]
        (assoc view
          :terminated true
          :cancelled true)
        view)))


; ----------------------------------------------------------------------------
;; A Master View

(defn item-manager-view
  [data]
  (let [spec
        (window "Item Manager"
                :content
                (panel "Content" :lygeneral "wrap 2, fill" :lycolumns "[|100,grow]" :lyrows "[|200,grow|]"
                       :components
                       [(label "Item") (textfield "item" :lyhint "growx")
                        (listbox "items" :lyhint "span, grow")
                        (panel "Actions" :lygeneral "ins 0" :lyhint "span, right"
                               :components
                               [(button "Add Item")
                                (button "Edit Item")
                                (button "Remove Item")
                                (button "Run Action")])]))]
    (-> (v/make-view "item-manager" spec)
        (assoc :mapping (v/make-mapping :item ["item" :text]
                                        :items ["items" :items]
                                        :selection ["items" :selection])
               :data data))))

(defn replace-at
  [xs n ys]
  (vec (concat (take n xs)
               ys
               (drop (inc n) xs))))

(defn item-manager-handler
  [view event]
  (go (assoc view
        :data
        (let [data (:data view)]
          (case ((juxt :source :type) event)
            
            ["Calc" :finished]
            (update-in data [:items] conj "Calc finished!")
            
            ["Run Action" :action]
            (do (future
                  (Thread/sleep 2000)
                  (async/>!! (:events view) {:source "Calc" :type :finished}))
                data)
            
            ["Add Item" :action]
            (-> data
                (update-in [:items] conj (:item data))
                (assoc :item ""))
            
            ["Edit Item" :action]
            (let [index (or (first (:selection data)) -1)
                  items (:items data)]
              (if (and (not= index -1) (< index (count items)))
                (let [editor-view (<! (v/run-view #'item-editor-view
                                                  #'item-editor-handler
                                                  {:text (nth items index)}))]
                  (if-not (:cancelled editor-view)
                    (assoc data
                      :items (replace-at items index [(-> editor-view :data :text)]))
                    data))
                data))
            
            ["Remove Item" :action]
            (assoc data
              :items (let [items (:items data)
                           index (or (first (:selection data)) -1)]
                       (if (and (not= index -1) (< index (count items)))
                         (replace-at items index [])
                         items)))
            data)))))



; ----------------------------------------------------------------------------
;; Startup


(defn start!
  []
  (v/run-tk (javafx/make-toolkit))
  (v/run-view #'item-manager-view
              #'item-manager-handler
              {:item ""
               :items (vec (take 100 (repeatedly #(rand-nth ["Foo" "Bar" "Baz"]))))}))


