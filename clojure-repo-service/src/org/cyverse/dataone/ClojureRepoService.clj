(ns org.cyverse.dataone.ClojureRepoService
  (:refer-clojure)
  (:import [org.dataone.service.types.v1 Identifier]
           [org.irods.jargon.core.pub DataAOHelper]
           [org.irods.jargon.core.query GenQueryOrderByField$OrderByType IRODSGenQueryBuilder QueryConditionOperators
            RodsGenQueryEnum]
           [org.irods.jargon.dataone.reposervice DataObjectListResponse])
  (:require [clojure.tools.logging :as log])
  (:gen-class :extends org.irods.jargon.dataone.reposervice.AbstractDataOneRepoServiceAO
              :init init
              :constructors {[org.irods.jargon.core.connection.IRODSAccount
                              org.irods.jargon.dataone.configuration.PublicationContext]
                             [org.irods.jargon.core.connection.IRODSAccount
                              org.irods.jargon.dataone.configuration.PublicationContext]}))

;; Default configuration settings.

(def ^:private default-repo-path "/TempZone/dataone")
(def ^:private default-pid-attr "dataone-pid")
(def ^:private default-dataone-publication-date-attr "dataone-pub-date")
(def ^:private default-page-length "50")

;; Functions to retrieve configuration settings.

(defn- get-additional-properties [this]
  (.. this getPublicationContext getAdditionalProperties))

(defn- get-dataone-repo-path [this]
  (.getProperty (get-additional-properties this) "irods.dataone.repo.path" default-repo-path))

(defn- get-dataone-pid-attr [this]
  (.getProperty (get-additional-properties this) "irods.dataone.attr.pid" default-pid-attr))

(defn- get-dataone-publication-date-attr [this]
  (.getProperty (get-additional-properties this) "irods.dataone.attr.pub-date" default-dataone-publication-date-attr))

(defn- get-query-page-length [this]
  (-> (.getProperty (get-additional-properties this) "irods.dataone.query-page-length" default-page-length)
      Integer/parseInt))

;; General query convenience functions.

(defn- get-query-executor [this]
  (.getIRODSGenQueryExecutor (.. this getPublicationContext getIrodsAccessObjectFactory)
                             (.getIrodsAccount this)))

(defn- lazy-rs
  "Converts a query result set into a lazy sequence of query result sets."
  [executor rs]
  (if (.isHasMoreRecords rs)
    (lazy-seq (cons rs (lazy-rs executor (.getMoreResults executor rs))))
    (do (.closeResults executor rs) [rs])))

(defn- lazy-gen-query
  "Performs a general query and returns a lazy sequence of results."
  [this query]
  (let [executor (get-query-executor this)]
    (mapcat (fn [rs] (.getResults rs))
            (lazy-rs executor (.executeIRODSQuery executor query 0)))))

(defn- gen-query
  "Performs a general query and returns the result set. This result set will be closed automatically."
  [this query offset]
  (.executeIRODSQueryAndCloseResult (get-query-executor this) query offset))

;; Common general query conditions.

(defn- in-repo? [builder this]
  (.addConditionAsGenQueryField builder
                                RodsGenQueryEnum/COL_COLL_NAME
                                QueryConditionOperators/LIKE
                                (str (get-dataone-repo-path this) "%")))

(defn- is-pid-attr? [builder this]
  (.addConditionAsGenQueryField builder
                                RodsGenQueryEnum/COL_META_DATA_ATTR_NAME
                                QueryConditionOperators/EQUAL
                                (get-dataone-pid-attr this)))

(defn- has-publication-date? [builder this]
  (.addConditionAsGenQueryField builder
                                RodsGenQueryEnum/COL_META_DATA_ATTR_NAME
                                QueryConditionOperators/EQUAL
                                (get-dataone-publication-date-attr this)))

;; Functions to retrieve the list of exposed identifiers.

(defn- build-id-query [this]
  (-> (IRODSGenQueryBuilder. true nil)
      (.addSelectAsGenQueryValue RodsGenQueryEnum/COL_META_DATA_ATTR_VALUE)
      (in-repo? this)
      (is-pid-attr? this)
      (has-publication-date? this)
      (.addOrderByGenQueryField RodsGenQueryEnum/COL_META_DATA_ATTR_VALUE GenQueryOrderByField$OrderByType/ASC)
      (.exportIRODSQueryFromBuilder (get-query-page-length this))))

(defn- list-exposed-identifiers [this]
  (mapv (fn [row] (.getColumn row 0))
        (lazy-gen-query this (build-id-query this))))

;; Functions to retrieve the list of exposed data objects.

(defn- build-data-object-query [this from-date to-date format-id count]
  (let [builder (IRODSGenQueryBuilder. true false true nil)]
    (DataAOHelper/addDataObjectSelectsToBuilder builder)
    (-> builder
        (.addSelectAsGenQueryValue RodsGenQueryEnum/COL_META_DATA_ATTR_NAME)
        (.addSelectAsGenQueryValue RodsGenQueryEnum/COL_META_DATA_ATTR_VALUE)
        (.addSelectAsGenQueryValue RodsGenQueryEnum/COL_META_DATA_ATTR_UNITS)
        (in-repo? this)
        (is-pid-attr? this)
        (has-publication-date? this)
        (.addOrderByGenQueryField RodsGenQueryEnum/COL_D_DATA_PATH GenQueryOrderByField$OrderByType/ASC)
        (.exportIRODSQueryFromBuilder count))))

(defn- list-exposed-data-objects [this from-date to-date format-id start-index count]
  (let [rs   (gen-query this (build-data-object-query this from-date to-date format-id count))
        rows (.getResults rs)]
    (doto (DataObjectListResponse.)
      (.setTotal (.getTotalRecords rs))
      (.setCount (count rows))
      (.setDataObjects (mapv (fn [row] (DataAOHelper/buildDomainFromResultSetRow row)) rows)))))

;; Class method implementations.

(defn -init [irods-account publication-context]
  [[irods-account publication-context] {}])

(defn -getListOfDataoneExposedIdentifiers [this]
  (mapv (fn [s] (doto (Identifier.) (.setValue s)))
        (list-exposed-identifiers this)))

(defn -getListOfDataoneExposedDataObjects [this from-date to-date format-id _ start-index count]
  (list-exposed-data-objects this from-date to-date format-id start-index count))
