(ns async-ui.core
  (:require [clojure.core.async :as async :refer [go go-loop put! <! >! close!]]
            [parsargs.core :as p]
            [examine.core :as e]))


; ------------------------------------------------------------------------------
;; Utilities

(def logging-on false)

(defn log [& xs] (when logging-on (apply println xs)) (last xs))

(defn as-vector [x]
  (cond
   (vector? x) x
   (coll? x)  (vec x)
   :else (vector x)))

; ------------------------------------------------------------------------------
;; Global state

(defonce views (atom {}))

(defonce tk-ch (atom nil))


; ------------------------------------------------------------------------------
;; A View is a map containing all data necessary for a UI view and the
;; visual component tree.
;;  - :id                   A string uniquely identifing this view
;;  - :spec                 A model of the form (see forml namespace)
;;  - :vc                   The tree of visual components
;;  - :data                 A map with all domain and view-state data of the form
;;  - :mapping              A vector of mappings between visual component
;;                          properties and data in the :data map
;;  - :events               The channel for receiving events
;;  - :setter-fns           A map of data-path to 1-arg functions used to update
;;                          visual component properties values
;;  - :validation-rule-set  A vector of validation rules (see examine library)
;;  - :validation-results   Current validation results (see examine library)

(defn make-view
  "Creates a map representing a View."
  [id spec]
  {:id id
   :spec spec
   :vc nil
   :data {}
   :mapping []
   :events (async/chan)
   :setter-fns {}
   :validation-rule-set []
   :validation-results {}})


; ------------------------------------------------------------------------------
;; An Event is a map with keys
;;  - :source   points to a visual components property or other source
;;  - :type     is a keyword denoting the type of event.
;;              common event types are :update or :action
;;  - :payload  is arbitrary data

(defn make-event
  "Creates and returns a map representing an Event.
  Used by Toolkit bindings."
  ([source type]
     (make-event source type nil))
  ([source type payload]
     {:source source
      :type type
      :payload payload})
  ([source property type payload]
     {:source [source property]
      :type type
      :payload payload}))


; ------------------------------------------------------------------------------
;; A Mapping is a vector of maps.
;; Each of these maps contains
;;  - :data-path      a vector or single keyword pointing to a piece of data in a map
;;  - :property-path  a vector consisting of a Component Path conj'ed with a keyword
;;                    denoting the property in the visual component.
;;  - :formatter      a function converting from a value to a human readable text
;;  - :parser         a function converting a human readable text to a value

(def ^:private mapping-parser 
  (p/some
   (p/sequence :data-path (p/alternative
                           (p/value vector?)
                           (p/value keyword?))
               :property-path (p/value #(and (vector? %)
                                             (every? string? (drop-last %))
                                             (keyword? (last %))))
               :formatter (p/optval fn? identity)
               :parser (p/optval fn? identity))))


(defn make-mapping
  "Returns a new Mapping vector from the arguments.
  Example:
  (make-mapping :foo [\"panel\" :text]) 
    results in
  [{:parser identity, 
    :formatter identity, 
    :property-path [\"Panel\" \"Foo\" :text], 
    :data-path :foo}]."
  [& args]
  (p/parse mapping-parser args))


(defn- matches-property-path?
  [event-source property-path]
  (if (vector? event-source)
    (= (reverse event-source) (->> property-path reverse (take 2)))
    (= event-source (->> property-path reverse second))))


; ----------------------------------------------------------------------------
;; A Toolkit provides uniform access to functionalities of Swing or JavaFX.

(defprotocol Toolkit
  (run-now [tk f]
    "Executes function f in toolkits event processing thread.")
  (show-view! [tk view]
    "Makes the root of the visual component tree visible.")
  (hide-view! [tk view]
    "Makes the root of the visual component tree invisible.")
  (build-vc-tree [tk view]
    "Creates a visual component tree from the data in the :spec slot of the view.
  Returns the view with an updated :vc slot.")
  (bind-vc-tree! [tk view]
    "Attaches listeners to visual components that put events to the :events channel of the view.
  Returns the view with :setter-fns slot updated.")
  (vc-name [tk vc]
    "Returns the name of the visual component.")
  (vc-children [tk vc]
    "Returns a seq with the children of the visual component or [] if it doesn't have any.")
  (set-vc-error! [tk vc msgs]
    "Updates the error state of a visual component according to the messages seq msgs.
  Empty msgs remove the error state."))


; ------------------------------------------------------------------------------
;; A Component Path is a vector of visual component names.
;; A Component Map contains all visual components indexed by their
;; corresponding component paths.

(defn- component-map
  "Returns a map from Component Path to visual component by
  traversing the visual component tree represented by it's root vc."
  [tk vc]
  (let [walk (fn walk [parent-path vc]
               (let [path (conj parent-path (vc-name tk vc))]
                 (cons
                  (vector path vc)
                  (mapcat (partial walk path) (vc-children tk vc)))))]
    (into {} (walk [] vc))))


(defn- find-by-path
  "Returns the first visual component whose component path matches 
  the given suffix."
  [comp-map path-suffix]
  (->> comp-map
       (filter #(every? true? (map = (reverse (first %)) (reverse (as-vector path-suffix)))))
       first
       second))


(defn setter-map
  "Returns a map {data-path -> setter}. The setter is a 1-arg function
  that retrieves the value from the data map arg according to the data-path of
  the mapping and writes the formatted value to mapped component property.
  Used by toolkit bindings."
  [tk vc update-fns mapping]
  (let [setter-fn-map (into {} (for [[cpath vc] (component-map tk vc),
                                     [key setter-fn] (update-fns vc)]
                                 [(conj cpath key) setter-fn]))]
    (into {}
          (for [{:keys [data-path property-path formatter]}  mapping]
            (if-let [setter-fn (find-by-path setter-fn-map property-path)]
              [data-path (fn set-value! [old-data data]
                           (let [data-path (as-vector data-path)
                                 old-value (get-in old-data data-path)
                                 new-value (get-in data data-path)]
                             (when (not= old-value new-value)
                               (log "run-tk: Set data" new-value "from" data-path)
                               (-> new-value formatter setter-fn))))])))))


; ----------------------------------------------------------------------------
;; Toolkit process

(defn- shutdown-view!
  "Hides a view, closes it's :events channel."
  [tk view]
  (log "run-tk: Shutting down view" (:id view))
  (close! (:events view))
  (hide-view! tk view)
  view)


(defn- create-vc-tree!
  "Builds and binds a view."
  [tk view]
  {:pre [(:spec view)
         (:id view)]
   :post [(:vc %)]}
  (if (or (nil? (:vc view)) (:rebuild view))
    (do (log "run-tk: Must build new visual component tree for" (:id view))
        (->> view
             (build-vc-tree tk)
             (bind-vc-tree! tk)))
    view))


(defn- update-data-in-vc-tree!
  "Calls all update functions in the view to put the views data
  into the visual component tree."
  [current-view {:keys [id setter-fns data] :as new-view}]
  {:pre [id data setter-fns]}
  (let [old-data (:data current-view)]
    (when (or (:rebuild new-view) (not= old-data data))
      (log "run-tk: Updating data in visual components of" id  ": " data)
      (doseq [setter (vals setter-fns)]
        (setter old-data data)))
    (dissoc new-view :rebuild)))


(defn- display-validation-results!
  "Takes :validation-results from view and sets validation error state
  in all visual components that are mentioned in the :mapping of the view."
  [tk view]
  {:pre [(:mapping view)
         (:validation-results view)
         (:vc view)]}
  (let [msg-map  (into {}
                       (for [{data-path :data-path property-path :property-path} (:mapping view),
                             :let [msgs (-> view :validation-results e/messages (get data-path))]]
                         [property-path msgs]))
        comp-map (component-map tk (:vc view))]
    (doseq [[property-path msgs] msg-map]
      (set-vc-error! tk
                     (find-by-path comp-map (drop-last property-path))
                     msgs))
    view))


(defn- synchronize-ui!
  "Creates and updates or removes the view."
  [tk current-view new-view]
  {:pre [(:id new-view)
         (:events new-view)]}
  (run-now tk
           (fn []
             (if (:terminated new-view)
               (swap! views dissoc (:id (shutdown-view! tk new-view)))
               (swap! views assoc (:id new-view) (->> new-view
                                                      (create-vc-tree! tk)
                                                      (update-data-in-vc-tree! current-view)
                                                      (display-validation-results! tk)))))))


(defn run-tk
  "Asynchronous process that waits for a view on the toolkit channel 
  and synchronizes it's state with the UI toolkit."
  [tk]
  (if-not @tk-ch
    (do
      (reset! tk-ch (async/chan))
      (go-loop []
        (try (let [view-msg  (<! @tk-ch)]
               (if-not (= :quit view-msg)
                 (do (try (let [view-id      (log "run-tk: Got view" (:id view-msg))
                                current-view (get @views view-id)
                                new-view     (assoc (merge current-view view-msg)
                                               :vc         (:vc current-view)
                                               :setter-fns (:setter-fns current-view))]
                            (synchronize-ui! tk current-view new-view))
                          (catch Exception ex
                            (.printStackTrace ex)))
                     (recur))
                 (do (log "run-tk: Quit")
                     (close! @tk-ch)
                     (reset! tk-ch nil)
                     :quit)))))
      (log "run-tk: Started"))
    (log "run-tk: Already running")))


(defn stop-tk
  []
  (when @tk-ch
    (put! @tk-ch :quit)))

; ----------------------------------------------------------------------------
;; View process


(defn- mapping-for-event
  "Returns the first mapping that matches the :source of the event."
  [mapping event]
  (->> mapping
       (filter #(matches-property-path? (:source event) (:property-path %)))
       first))


(defn- update-view
  "Updates a view from an event and validates its data.
  If the event is nil or of type :close then [:terminated true] is added.
  Returns an updated view."
  [view event]
  {:pre [(:data view)]
   :post [(:data %)]}
  (condp = (if event (:type event) :close)
    :update (if-let [m (mapping-for-event (:mapping view) event)]
              (let [data (assoc-in (:data view)
                                   (-> m :data-path as-vector)
                                   ((:parser m) (:payload event)))
                    vrs (e/validate (-> view :validation-rule-set (e/sub-set (:data-path m))) data)]
                (assoc view
                  :data data
                  :validation-results (merge (:validation-results view) vrs)))
              view)
    :close (assoc view :terminated (or (:terminated view) (= :close (:type event))))
    view))


(defn- install-automatic-rebuild!
  "Adds a watch on the factory function var that causes a rebuild of
  the visual component tree. Enables interactive UI form development
  in the REPL."
  [view-factory-var initial-data]
  (add-watch view-factory-var
             :rebuild
             (fn [k r o new-factory]
               (put! @tk-ch (let [new-view (new-factory initial-data)
                                 old-view (@views (:id new-view))]
                             (assoc new-view
                               :rebuild true
                               :events (:events old-view)
                               :vc (:vc old-view)
                               :data (:data old-view)))))))

(defn run-view
  "Asynchronous process that waits for events on the views :events channel,
  processes the event (update of data, validation, further actions represented
  by the handler-fn-var), and finally pushes the resulting view onto the central 
  toolkit channel."
  [view-factory-var handler-fn-var initial-data]
  {:pre [@tk-ch]}
  (install-automatic-rebuild! view-factory-var initial-data)
  (go-loop [view (@view-factory-var initial-data)]
    (let [view-id (:id view)]
      (>! @tk-ch view)
      (if-not (:terminated view)
        (let [event (log "run-view" view-id ": Got event" (<! (:events view)))
              new-view (<! (@handler-fn-var (update-view view event) event))]
          (log "run-view" view-id
               "Data" (:data new-view)
               "Validation Result" (e/messages (:validation-results new-view)))
          (recur new-view))
        (do
          (log "run-view" view-id ": Terminating process")
          (remove-watch view-factory-var :rebuild)
          view)))))
