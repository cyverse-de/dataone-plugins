(ns org.cyverse.dataone.repo-test
  (:require [clojure.java.io :as io]
            [dwr71.jargon-repl :as jr])
  (:import [java.util Properties
            org.irods.jargon.dataone.plugin PublicationContext]))

(defn- load-properties []
  (if-let [url (io/resource "irods.properties")]
    (doto (Properties.)
      (let [in (reader url)]
        (try
          (load in)
          (finally (.close in)))))
    (throw (Exception. "irods.properties not found in classpath"))))

(defn- connection-params [props]
  {:host     (.getProperty props "irods.host" "localhost")
   :port     (Integer/parseInt (.getProperty props "irods.port" "1247"))
   :user     (.getProperty props "irods.user" "someuser")
   :password (.getProperty props "irods.password" "notprod")
   :home     (.getProperty props "irods.home" "/iplant/home")
   :zone     (.getProperty props "irods.zone" "iplant")})

(defn- get-rest-configuration []
  nil)

(defn- get-publication-context [aof acct props]
  (doto (PublicationContext.)
    (.setIrodsAccessObjectFactory aof)
    (.setRestConfiguration (get-rest-configuration))
    (.setAdditionalProperties props)
    (.setPluginDiscoveryService nil)))

(defn get-factory []
  (let [props (load-properties)
        aof   (jr/get-access-object-factory)
        acct  (jr/get-account aof (connection-params props))
        ctx   (get-publication-context aof acct props)]))
