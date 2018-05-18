(ns org.cyverse.dataone.event-test
  (:require [clojure.java.io :as io]
            [dwr71.jargon-repl :as jr])
  (:import [java.util Properties]
           [org.cyverse.dataone DataOneEventServiceFactory]
           [org.irods.jargon.dataone.configuration RestConfiguration]
           [org.irods.jargon.dataone.plugin PublicationContext]))

(defn- load-properties []
  (if-let [url (io/resource "irods.properties")]
    (let [in (io/reader url)]
      (try
        (doto (Properties.)
          (.load in))
        (finally (.close in))))
    (throw (Exception. "irods.properties not found in classpath"))))

(defn- connection-params [props]
  {:host     (.getProperty props "irods.host" "localhost")
   :port     (Integer/parseInt (.getProperty props "irods.port" "1247"))
   :user     (.getProperty props "irods.user" "someuser")
   :password (.getProperty props "irods.password" "notprod")
   :home     (.getProperty props "irods.home" "/iplant/home")
   :zone     (.getProperty props "irods.zone" "iplant")})

(defn- get-rest-configuration [cparams]
  (doto (RestConfiguration.)
    (.setIrodsHost (:host cparams))
    (.setIrodsPort (:port cparams))
    (.setIrodsZone (:zone cparams))
    (.setIrodsUserName (:user cparams))
    (.setIrodsUserPswd (:password cparams))))

(defn- get-publication-context [aof acct props cparams]
  (doto (PublicationContext.)
    (.setIrodsAccessObjectFactory aof)
    (.setRestConfiguration (get-rest-configuration cparams))
    (.setAdditionalProperties props)
    (.setPluginDiscoveryService nil)))

(defn get-event-service []
  (let [props   (load-properties)
        cparams (connection-params props)
        aof     (jr/get-access-object-factory)
        acct    (jr/get-account aof cparams)
        ctx     (get-publication-context aof acct props cparams)]
    (.instance (DataOneEventServiceFactory.) ctx acct)))
