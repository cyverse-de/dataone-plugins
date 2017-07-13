(defproject org.cyverse/clojure-repo-service "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.cyverse/metadata-client "3.0.1-SNAPSHOT"]
                 [org.dataone/d1_libclient_java "2.3.0"]
                 [org.irods/dataone-plugin "4.2.1.0-SNAPSHOT"]
                 [org.irods.jargon/jargon-core "4.2.1.0-SNAPSHOT"]]
  :profiles {:repl {:dependencies [[dwr71/jargon-repl "0.0.1-SNAPSHOT"]]}}
  :repositories [["dice.repository.snapshots" {:url "https://raw.github.com/DICE-UNC/DICE-Maven/master/snapshots"}]
                 ["dice.repository" {:url "https://raw.github.com/DICE-UNC/DICE-Maven/master/releases"}]]
  :aot :all)
