(ns org.cyverse.dataone.ClojureRepoService
  (:refer-clojure)
  (:import [org.dataone.service.types.v1 Identifier]
           [org.irods.jargon.core.exception FileNotFoundException]
           [org.irods.jargon.core.pub DataAOHelper]
           [org.irods.jargon.core.query CollectionAndDataObjectListingEntry$ObjectType IRODSGenQueryBuilder
            QueryConditionOperators]
           [org.irods.jargon.dataone.model CollectionDataOneObject DataOneObjectListResponse]
           [java.util Date])
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log])
  (:gen-class :extends org.irods.jargon.dataone.reposervice.AbstractDataOneRepoServiceAO
              :init init
              :constructors {[org.irods.jargon.core.connection.IRODSAccount
                              org.irods.jargon.dataone.plugin.PublicationContext]
                             [org.irods.jargon.core.connection.IRODSAccount
                              org.irods.jargon.dataone.plugin.PublicationContext]}))

;; Default configuration settings.

(def ^:private default-uuid-attr "ipc_UUID")
(def ^:private default-root "/iplant/home/shared/commons_repo/curated")
(def ^:private default-page-length "50")
(def ^:private default-format "application/octet-stream")

;; Functions to retrieve configuration settings.

(defn- get-additional-properties [this]
  (.. this getPublicationContext getAdditionalProperties))

(defn- get-property [this name default]
  (.getProperty (get-additional-properties this) name default))

(defn- get-uuid-attr [this]
  (get-property this "cyverse.avu.uuid-attr" default-uuid-attr))

(defn- get-root [this]
  (get-property this "cyverse.dataone.root" default-root))

(defn- get-query-page-length [this]
  (-> (get-property this "irods.dataone.query-page-length" default-page-length)
      Integer/parseInt))

;; General convenience functions.

(defn- get-time [d]
  (when d
    (.getTime d)))

;; General jargon convenience functions.

(defn- get-collection-ao [this]
  (.getCollectionAO (.. this getPublicationContext getIrodsAccessObjectFactory)
                    (.getIrodsAccount this)))

(defn- get-file-system-ao [this]
  (.getIRODSFileSystemAO (.. this getPublicationContext getIrodsAccessObjectFactory)
                         (.getIrodsAccount this)))

(defn- get-gen-query-executor [this]
  (.getIRODSGenQueryExecutor (.. this getPublicationContext getIrodsAccessObjectFactory)
                             (.getIrodsAccount this)))

;; Functions to retrieve the list of exposed identifiers.

(defn- list-matching-identifiers [this from-date to-date])

;; Functions to retrieve the list of exposed data objects.

(defn- add-modify-time-condition [builder operator date]
  (.addConditionAsGenQueryField builder "DATA_MODIFY_TIME" operator (quot (.getTime date) 1000)))

(defn- add-coll-name-condition [builder operator value]
  (.addConditionAsGenQueryField builder "COLL_NAME" operator value))

(defn- build-data-object-listing-query [this from-date to-date]
  (-> (IRODSGenQueryBuilder. true false nil)
      (DataAOHelper/addDataObjectSelectsToBuilder)
      (add-modify-time-condition QueryConditionOperators/NUMERIC_GREATER_THAN_OR_EQUAL_TO from-date)
      (add-modify-time-condition QueryConditionOperators/NUMERIC_LESS_THAN_OR_EQUAL_TO to-date)
      (add-coll-name-condition QueryConditionOperators/LIKE (str (get-root this) "%"))))

;; TODO: finish implementing this function. We need to get the result set and verify that we can extract all of the
;; results in the case where the limit is greater than the query length or not defined at all. This could cause a
;; problem or slowness when we're closing the result set between pages.
(defn- list-exposed-data-objects [this from-date to-date start-index limit]
  (->> (build-data-object-listing-query this from-date to-date)
       (.exportIRODSQueryFromBuilder (get-query-page-length))
       (.executeIRODSQueryAndCloseResult (or start-index 0))))

;; Last modification date functions.

(defn- get-last-modified-date [this path]
  (try
    (let [stat (.getObjStat (get-file-system-ao this) path)]
      (when (= (.getObjectType stat)
               (CollectionAndDataObjectListingEntry$ObjectType/DATA_OBJECT))
        (.getModifiedAt stat)))
    (catch FileNotFoundException e nil)))

;; Class method implementations.

(defn -init [irods-account publication-context]
  [[irods-account publication-context] {}])

(defn -getListOfDataoneExposedIdentifiers [this]
  (mapv (fn [s] (doto (Identifier.) (.setValue (:value s))))
        (list-exposed-identifiers this)))

(defn -getExposedObjects [this from-date to-date format-id _ start-index limit]
  (if (or (nil? (some-> format-id .getValue)) (= (.getValue format-id) default-format))
    (try
      (log/spy :warn (list-exposed-data-objects this from-date to-date start-index limit))
      (catch Throwable t
        (log/error t)
        (throw t)))
    (DataOneObjectListResponse. [] 0 (or start-index 0))))

(defn -getLastModifiedDate [this path]
  (get-last-modified-date this path))

(defn -getFormat [_ _]
  default-format)
