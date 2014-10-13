(ns async-ui.ex-master-detail
  (:require [clojure.core.async :refer [go <! >!] :as async]
            [examine.core :as e]
            [examine.constraints :as c]
            [async-ui.forml :refer :all]
            [async-ui.core :as v]
            [async-ui.javafx.tk :as tk]))


; ----------------------------------------------------------------------------
;; TODOs
;; - Demonstrate testing support, event recording and play-back
;; - Simulate a long-running call
;; - 1-arg setter-fn should become 2-arg update-fn to improve performance

; ----------------------------------------------------------------------------
;; In the REPL:
;; Compile this namespace.

;; Run this snippet
#_(do
  (ns async-ui.ex-master-detail)
  (def t (tk/make-toolkit))
  (v/run-tk t))

;; Start process for master view
#_(v/run-view #'item-manager-view
              #'item-manager-handler
              {:item ""
               :items ["Foo" "Bar" "Baz"]})

;; We could start the process for the details view directly
#_(v/run-view #'item-editor-view
              #'item-editor-handler
              {:text "Foo"})


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
  (concat (take n xs)
          ys
          (drop (inc n) xs)))

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
            (let [index (or (first (:selection data)) -1)]
              (if (not= index -1)
                (let [items       (:items data)
                      editor-view (<! (v/run-view #'item-editor-view
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
                       (if (not= index -1)
                         (replace-at items index [])
                         items)))
            data)))))

