(ns async-ui.ex-table
  (:require [clojure.core.async :refer [go <! >!] :as async]
            [async-ui.forml :refer :all]
            [async-ui.core :as v]
            [async-ui.javafx.tk :as javafx]))



(defn table-view
  [data]
  (-> (v/make-view
       "table-demo"
       (window "Table Demo" :content
               (panel "Content"
                      :components
                      [(table "Contacts"
                              :columns
                              [(column "Name")
                               (column "Street")
                               (column "City")])])))
      (assoc :data data
             :mapping (v/make-mapping :contacts ["Contacts" :items]))))


(defn table-view-handler
  [view event]
  (go view))


(defn start! []
  (v/run-view #'table-view
              #'table-view-handler
              {:contacts [{:name "foo" :street "bar" :city "baz"}]}))
