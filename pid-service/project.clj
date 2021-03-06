;; Normally we wouldn't want to do this, but some of the dependencies we need appear to be available only over
;; plain HTTP.
(require 'cemerick.pomegranate.aether)
(cemerick.pomegranate.aether/register-wagon-factory!
 "http" #(org.apache.maven.wagon.providers.http.HttpWagon.))

(defproject org.cyverse/dataone-pid-service "0.1.0-SNAPSHOT"
  :description "Plugin for the DataONE member node service."
  :license {:name "BSD"
            :url "http://www.cyverse.org/sites/default/files/iPLANT-LICENSE.txt"}
  :uberjar-name "dataone-pid-service-standalone.jar"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.dataone/d1_libclient_java "2.3.0"
                  :exclusions [org.bouncycastle/bcpkix-jdk15on
                               org.bouncycastle/bcprov-jdk15on]]
                 [org.irods/dataone-plugin "4.2.1.0-SNAPSHOT"]
                 [org.irods.jargon/jargon-core "4.2.1.0-SNAPSHOT"]]
  :repositories [["dataone.maven" {:url "http://maven.dataone.org/"}]
                 ["internal.snapshots" {:url "https://raw.github.com/slr71/maven/master/snapshots"}]
                 ["dice.repository.snapshots" {:url "https://raw.github.com/DICE-UNC/DICE-Maven/master/snapshots"}]
                 ["dice.repository" {:url "https://raw.github.com/DICE-UNC/DICE-Maven/master/releases"}]]
  :prep-tasks [["compile" "org.cyverse.dataone.DataOnePidService"]
               "javac" "compile"]
  :source-paths ["src/clojure"]
  :java-source-paths ["src/java"]
  :javac-options ["-target" "1.8" "-source" "1.8"]
  :aot :all)
