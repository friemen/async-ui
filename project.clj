(defproject async-ui "0.1.0-SNAPSHOT"
  :description "Demonstrating how to apply core.async to rich clients"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]                 
                 [org.clojure/core.async "0.1.278.0-76b25b-alpha"]
                 [metam/core "1.0.5"]
                 [parsargs "1.2.0"]
                 [examine "1.1.0"]
                 ; Swing
                 [com.miglayout/miglayout-swing "4.2"]
                 ; JavaFX
                 [com.miglayout/miglayout-javafx "4.2"]])
