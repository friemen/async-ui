(defproject async-ui "0.1.0-SNAPSHOT"
  :description "Demonstrating how to apply core.async to rich clients"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [metam/core "1.0.5"]
                 [parsargs "1.2.0"]
                 [examine "1.2.0"]
                 [environ "1.0.0"]
                 ;; Swing
                 [com.miglayout/miglayout-swing "5.0"]
                 ;; JavaFX
                 [com.miglayout/miglayout-javafx "5.0"]]
  :main async-ui.main
  :plugins [[lein-environ "1.0.0"]]
  :profiles {:uberjar {:aot :all
                       :env {:javafx-exit true}}}
  :repositories [["sonatype" {:url "https://oss.sonatype.org/content/repositories/snapshots"
                              :snapshots true}]])
