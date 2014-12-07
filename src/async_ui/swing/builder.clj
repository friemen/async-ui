(ns async-ui.swing.builder
  "Swing UI builder"
  (:require [async-ui.forml :as f]
            [metam.core :refer [metatype metatype?]])
  (:import [javax.swing AbstractListModel
            JButton JFrame JLabel JList JPanel JTable JScrollPane JTextField
            ListSelectionModel]
           [javax.swing.table DefaultTableCellRenderer DefaultTableColumnModel
            TableColumn TableCellRenderer TableModel]
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
  (throw (IllegalArgumentException.
          (str "Cannot build type '"
               (metatype spec)
               "'. Did you implement a build method for this type?"))))


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
  (let [l  (make JList spec)
        sp (JScrollPane. l)]
    (doto l
      (.setModel (proxy [AbstractListModel] []
                   (getSize []
                     (-> l (.getClientProperty :data) count))
                   (getElementAt [row]
                     (-> l (.getClientProperty :data) (nth row))))))
    sp))


(defmethod build ::f/panel
  [spec]
  (let [p (doto (make JPanel spec)
            (.setLayout (MigLayout. (:lygeneral spec) (:lycols spec) (:lyrows spec))))]
    (doseq [child (:components spec)]
      (let [vc (build child)]
        (.add p vc (:lyhint child))))
    p))


(declare table-column-model)

(defmethod build ::f/table
  [spec]
  (let [t    (make JTable spec)
        sp   (JScrollPane. t)
        cols (:columns spec)]
    (doto t
      (.setColumnModel (table-column-model (-> spec :columns)))
      (.setAutoCreateColumnsFromModel false)
      (.setSelectionMode ListSelectionModel/SINGLE_SELECTION)
      (.setModel (reify TableModel
                   (getColumnClass [_ column]
                     java.lang.String)
                   (getColumnName [_ column]
                     (get-in cols [column :title]))
                   (getColumnCount [_]
                     (count cols))
                   (getRowCount [_]
                     (count (.getClientProperty t :data)))
                   (getValueAt [_ row column]
                     (let [getter-fn (get-in cols [column :getter])]
                       (-> (.getClientProperty t :data)
                           (nth row)
                           (getter-fn))))
                   (isCellEditable [_ row column]
                     false)
                   (setValueAt [_ row column v]
                     nil)
                   (addTableModelListener [_ l])
                   (removeTableModelListener [_ l]))))
    sp))


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



; ------------------------------------------------------------------------------
;; Setup table

        
(defn- table-column-model
  [column-specs]
  (let [tcm (DefaultTableColumnModel.)]
    (doseq [[i c] (map vector (range) column-specs)] 
      (.addColumn tcm
                  (doto (TableColumn.)
                    (.setIdentifier c)
                    (.setModelIndex i)
                    (.setHeaderValue (-> c :title)))))
    tcm))


