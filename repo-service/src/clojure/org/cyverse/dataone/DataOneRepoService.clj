(ns org.cyverse.dataone.DataOneRepoService
  (:refer-clojure)
  (:import [org.dataone.service.types.v1 Identifier]
           [org.irods.jargon.core.exception FileNotFoundException]
           [org.irods.jargon.core.pub DataAOHelper]
           [org.irods.jargon.core.query AVUQueryElement AVUQueryElement$AVUQueryPart
            CollectionAndDataObjectListingEntry$ObjectType GenQueryOrderByField$OrderByType
            IRODSGenQueryBuilder QueryConditionOperators RodsGenQueryEnum]
           [org.irods.jargon.dataone.model DataOneObjectListResponse FileDataOneObject]
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
(def ^:private default-format-id-attr "ipc-d1-format-id")
(def ^:private default-offset 0)
(def ^:private default-limit 500)

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

(defn- get-format-id-attr [this]
  (get-property this "cyverse.dataone.format-id-attr" default-format-id-attr))

;; General convenience functions.

(defn- identifier-from-string [s]
  (doto (Identifier.)
    (.setValue s)))

(defn- epoch-to-date [s]
  (Date. (* 1000 (Long/parseLong s))))

;; General jargon convenience functions.

(defn- get-collection-ao [this]
  (.getCollectionAO (.. this getPublicationContext getIrodsAccessObjectFactory)
                    (.getIrodsAccount this)))

(defn- get-data-object-ao [this]
  (.getDataObjectAO (.. this getPublicationContext getIrodsAccessObjectFactory)
                    (.getIrodsAccount this)))

(defn- get-file-system-ao [this]
  (.getIRODSFileSystemAO (.. this getPublicationContext getIrodsAccessObjectFactory)
                         (.getIrodsAccount this)))

(defn- get-gen-query-executor [this]
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
  [this offset query]
  (let [executor (get-gen-query-executor this)]
    (mapcat (fn [rs] (.getResults rs))
            (lazy-rs executor (.executeIRODSQuery executor query (or offset default-offset))))))

(defn- gen-query
  "Performs a general query and returns the result set. This result set will be closed automatically."
  [this offset query]
  (.executeIRODSQueryAndCloseResult (get-gen-query-executor this) query offset))

(defn- add-data-object-selects [builder]
  (DataAOHelper/addDataObjectSelectsToBuilder builder)
  builder)

(defn- add-modify-time-condition [builder operator date]
  (if-not (nil? date)
    (let [timestamp (format "%011d" (quot (.getTime date) 1000))]
      (.addConditionAsGenQueryField builder RodsGenQueryEnum/COL_D_MODIFY_TIME operator timestamp))
    builder))

(defn- add-coll-name-condition [builder operator value]
  (.addConditionAsGenQueryField builder RodsGenQueryEnum/COL_COLL_NAME operator value))

(defn- add-attribute-name-condition [builder operator value]
  (.addConditionAsGenQueryField builder RodsGenQueryEnum/COL_META_DATA_ATTR_NAME operator value))

(defn- add-replica-number-condition [builder operator value]
  (.addConditionAsGenQueryField builder RodsGenQueryEnum/COL_DATA_REPL_NUM operator value))

(defn- is-file?
  ([this path]
   (is-file? (.getObjStat (get-file-system-ao this) path)))
  ([stat]
   (= (.getObjectType stat) (CollectionAndDataObjectListingEntry$ObjectType/DATA_OBJECT))))

(defn- avu-query [attr]
  [(AVUQueryElement/instanceForValueQuery AVUQueryElement$AVUQueryPart/ATTRIBUTE
                                         QueryConditionOperators/EQUAL
                                         attr)])

;; Functions to retrieve the list of exposed identifiers.

(defn- build-id-query [this]
  (-> (IRODSGenQueryBuilder. true false true nil)
      (.addSelectAsGenQueryValue RodsGenQueryEnum/COL_META_DATA_ATTR_VALUE)
      (.addOrderByGenQueryField RodsGenQueryEnum/COL_META_DATA_ATTR_VALUE GenQueryOrderByField$OrderByType/ASC)
      (add-coll-name-condition QueryConditionOperators/LIKE (str (get-root this) "%"))
      (add-attribute-name-condition QueryConditionOperators/EQUAL (get-uuid-attr this))
      (.exportIRODSQueryFromBuilder (get-query-page-length this))))

(defn- list-exposed-identifiers [this]
  (mapv (fn [row] (identifier-from-string (.getColumn row 0)))
        (lazy-gen-query this 0 (build-id-query this))))

;; Functions to retrieve the list of exposed data objects.

(defn- build-data-object-listing-query [this from-date to-date limit]
  (-> (IRODSGenQueryBuilder. true false true nil)
      add-data-object-selects
      (.addSelectAsGenQueryValue RodsGenQueryEnum/COL_META_DATA_ATTR_NAME)
      (.addSelectAsGenQueryValue RodsGenQueryEnum/COL_META_DATA_ATTR_VALUE)
      (.addSelectAsGenQueryValue RodsGenQueryEnum/COL_META_DATA_ATTR_UNITS)
      (add-modify-time-condition QueryConditionOperators/GREATER_THAN_OR_EQUAL_TO from-date)
      (add-modify-time-condition QueryConditionOperators/LESS_THAN_OR_EQUAL_TO to-date)
      (add-coll-name-condition QueryConditionOperators/LIKE (str (get-root this) "%"))
      (add-attribute-name-condition QueryConditionOperators/EQUAL (get-uuid-attr this))
      (add-replica-number-condition QueryConditionOperators/EQUAL 0)
      (.addOrderByGenQueryField RodsGenQueryEnum/COL_D_DATA_PATH GenQueryOrderByField$OrderByType/ASC)
      (.exportIRODSQueryFromBuilder limit)))

(defn- file-data-one-object-from-row [this row]
  (FileDataOneObject.
   (.getPublicationContext this)
   (.getIrodsAccount this)
   (identifier-from-string (.getColumn row (.getName RodsGenQueryEnum/COL_META_DATA_ATTR_VALUE)))
   (epoch-to-date (.getColumn row (.getName RodsGenQueryEnum/COL_D_MODIFY_TIME)))
   (DataAOHelper/buildDomainFromResultSetRow row)))

(defn- list-exposed-data-objects [this from-date to-date start-index count]
  (let [rs       (gen-query this start-index (build-data-object-listing-query this from-date to-date count))
        elements (mapv (partial file-data-one-object-from-row this) (.getResults rs))]
    (DataOneObjectListResponse. elements (.getTotalRecords rs) start-index)))

;; Last modification date functions.

(defn- get-last-modified-date [this path]
  (try
    (let [stat (.getObjStat (get-file-system-ao this) path)]
      (when (is-file? stat)
        (.getModifiedAt stat)))
    (catch FileNotFoundException e nil)))

;; Format ID functions.

(defn- get-data-object-format [this path]
  (let [data-object-ao (get-data-object-ao this)]
    (some-> (.findMetadataValuesForDataObjectUsingAVUQuery data-object-ao (avu-query (get-format-id-attr this)) path)
            first
            (.getAvuValue))))

(defn- get-format [this path]
  (if (is-file? this path)
    (get-data-object-format this path)
    default-format))

;; Class method implementations.

(defn -init [irods-account publication-context]
  [[irods-account publication-context] {}])

(defn -getListOfDataoneExposedIdentifiers [this]
  (list-exposed-identifiers this))

(defn -getExposedObjects [this from-date to-date format-id _ offset limit]
  (let [offset (or offset default-offset)
        limit  (or limit default-limit)]
    (if (or (nil? (some-> format-id .getValue)) (= (.getValue format-id) default-format))
      (try
        (list-exposed-data-objects this from-date to-date offset limit)
        (catch Throwable t
          (log/error t)
          (throw t)))
      (DataOneObjectListResponse. [] 0 0))))

(defn -getLastModifiedDate [this path]
  (get-last-modified-date this path))

(defn -getFormat [this path]
  (or (get-format this path) default-format))
