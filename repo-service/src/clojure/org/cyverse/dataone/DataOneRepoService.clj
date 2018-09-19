(ns org.cyverse.dataone.DataOneRepoService
  (:refer-clojure)
  (:import [org.dataone.service.types.v1 Identifier]
           [org.irods.jargon.core.exception FileNotFoundException]
           [org.irods.jargon.core.pub DataAOHelper]
           [org.irods.jargon.core.query AVUQueryElement AVUQueryElement$AVUQueryPart
            CollectionAndDataObjectListingEntry$ObjectType GenQueryOrderByField$OrderByType
            IRODSGenQueryBuilder IRODSQueryResultSetInterface QueryConditionOperators RodsGenQueryEnum]
           [org.irods.jargon.core.exception DataNotFoundException]
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
(def ^:private default-metadata-root "/iplant/home/shared/commons_repo/curated_metadata")
(def ^:private default-page-length "50")
(def ^:private default-format "application/octet-stream")
(def ^:private default-format-id-attr "ipc-d1-format-id")
(def ^:private default-offset 0)
(def ^:private default-limit 500)
(def ^:private max-limit 500)

;; Functions to retrieve configuration settings.

(defn- get-additional-properties [this]
  (.. this getPublicationContext getAdditionalProperties))

(defn- get-property [this name default]
  (.getProperty (get-additional-properties this) name default))

(defn- get-uuid-attr [this]
  (get-property this "cyverse.avu.uuid-attr" default-uuid-attr))

(defn- get-root [this]
  (get-property this "cyverse.dataone.root" default-root))

(defn- get-metadata-root [this]
  (get-property this "cyverse.dataone.metadata-root" default-metadata-root))

(defn- get-roots [this]
  (sort ((juxt get-root get-metadata-root) this)))

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
  "Performs a general query and returns a lazy sequence of results. Offsets are not supported yet."
  [this query]
  (let [executor (get-gen-query-executor this)]
    (mapcat (fn [rs] (.getResults rs))
            (lazy-rs executor (.executeIRODSQuery executor query default-offset)))))

(defn- lazy-gen-queries
  "Performs multiple general queries and returns a lazy sequence of results. Offsets are not supported yet."
  [this queries]
  (mapcat (fn [query] (lazy-gen-query this query)) queries))

(defn- gen-query
  "Performs a general query and returns the result set. This result set will be closed automatically."
  [this offset query]
  (.executeIRODSQueryAndCloseResult (get-gen-query-executor this) query offset))

(defn- run-query
  "Jargon throws an exception if we run a query with a limit of zero, but we need to be able to do that in order
   to treat the results of multiple queries as a single result."
  [this query-builder offset limit]
  (let [executor (get-gen-query-executor this)
        query    (.exportIRODSQueryFromBuilder query-builder (max limit 1))
        rs       (.executeIRODSQueryAndCloseResult executor query offset)]
    (reify
      org.irods.jargon.core.query.IRODSQueryResultSetInterface

      ;; It's sufficient to return the column names from the original result set.
      (getColumnNames [_]
        (.getColumnNames rs))

      ;; We can only get the first result if the limit is greater than zero. Also, Jargon throws an exception
      ;; if this method is called when there are no results. In our use case, it's more convenient to return
      ;; nil if there are no results to return.
      (getFirstResult [_]
        (when (pos? limit)
          (try
            (.getFirstResult rs)
            (catch org.irods.jargon.core.exception.DataNotFoundException _ nil))))

      ;; It's sufficient to return the number of columns from the original result set.
      (getNumberOfResultColumns [_]
        (.getNumberOfResultColumns rs))

      ;; The set of results should be empty if the limit is zero.
      (getResults [_]
        (if (pos? limit)
          (vec (.getResults rs))
          []))

      ;; It's sufficient to return the total number of records from the original result set.
      (getTotalRecords [_]
        (.getTotalRecords rs))

      ;; If the limit is zero then the original result set's isHasMoreRecords method will still return a false
      ;; value if there was exactly one more matching record.
      (isHasMoreRecords [+]
        (if (pos? limit)
          (.isHasMoreRecords rs)
          (pos? (count (.getResults rs))))))))

(defn- run-queries
  "Runs multiple general queries and returns a sequence of result sets. This is a workaround for iRODS general
   queries not supporting `or` conditions. The columns returned by all queries passed to this function must be
   identical."
  [this query-builders offset limit]
  (loop [[builder & builders] query-builders
         offset               (max offset 0)
         limit                (max limit 0)
         acc                  []]
    (if builder
      (let [rs    (run-query this builder offset limit)
            size  (count (.getResults rs))
            total (.getTotalRecords rs)]
        (if (= size 0)
          (recur builders (max (- offset total) 0) limit (conj acc rs))
          (recur builders 0 (max (- limit size) 0) (conj acc rs))))
      acc)))

(defn- gen-queries
  "Performs multiple general queries and returns a combined result set. The result set will be closed automatically.
   The columns returned by all queries passed to this function must be identical."
  [this query-builders offset limit]
  (let [rss (run-queries this query-builders offset limit)]
    (reify
      org.irods.jargon.core.query.IRODSQueryResultSetInterface

      ;; Since we're assuming that all queries return the same set of columns, this method only returns the column
      ;; names of the first result set.
      (getColumnNames [_]
        (.getColumnNames (first rss)))

      ;; The first result set may not contain any records, so it's necessary to check every result set.
      (getFirstResult [this]
        (first (.getResults this)))

      ;; Since we're assuming that all queries return the same set of columns, this method only returns the number of
      ;; columns in the first result set.
      (getNumberOfResultColumns [_]
        (.getNumberOfResultColumns [_]))

      ;; This implementation combines all of the results into a single collection.
      (getResults [_]
        (mapcat #(vec (.getResults %)) rss))

      ;; This implementation adds the total records from all queries into a single collection.
      (getTotalRecords [_]
        (reduce #(+ %1 (.getTotalRecords %2)) 0 rss))

      ;; There are more records to return if any result set has more records to return.
      (isHasMoreRecords [_]
        (some #(.isHasMoreRecords rss))))))

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

(defn- add-attribute-value-condition [builder operator value]
  (.addConditionAsGenQueryField builder RodsGenQueryEnum/COL_META_DATA_ATTR_VALUE operator value))

(defn- add-replica-number-condition [builder operator value]
  (.addConditionAsGenQueryField builder RodsGenQueryEnum/COL_DATA_REPL_NUM operator value))

(defn- add-exclusions [builder column exclusions]
  (if (seq exclusions)
    (reduce #(.addConditionAsGenQueryField %1 column QueryConditionOperators/NOT_EQUAL %2) builder exclusions)
    builder))

(defn- add-inclusions [builder column inclusions]
  (if (seq inclusions)
    (.addConditionAsMultiValueCondition builder column QueryConditionOperators/IN inclusions)
    builder))

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

(defn- build-base-id-query [this]
  (-> (IRODSGenQueryBuilder. true false true nil)
      (.addSelectAsGenQueryValue RodsGenQueryEnum/COL_META_DATA_ATTR_VALUE)
      (.addOrderByGenQueryField RodsGenQueryEnum/COL_META_DATA_ATTR_VALUE GenQueryOrderByField$OrderByType/ASC)
      (add-attribute-name-condition QueryConditionOperators/EQUAL (get-uuid-attr this))
      (.exportIRODSQueryFromBuilder (get-query-page-length this))))

;; I haven't quite figured out why yet, but the second query doesn't return the expected results when the first query is
;; included in the list of queries. We don't expect to have files directly in the root directory at this time, so checks
;; for them will be omitted.
(defn- build-base-id-queries [this root]
  [#_(-> (build-base-id-query this)
         (add-coll-name-condition QueryConditionOperators/EQUAL root))
   (-> (build-base-id-query this)
       (add-coll-name-condition QueryConditionOperators/LIKE (str root "/%")))])

(defn- build-id-queries [this]
  (mapcat #(build-base-id-queries this %) (get-roots this)))

(defn- list-exposed-identifiers [this]
  (->> (build-id-queries this)
       (lazy-gen-queries this)
       (mapv (fn [row] (identifier-from-string (.getColumn row 0))))))

;; Functions to retrieve the list of exposed data objects.

(defn- build-base-data-object-listing-query [this from-date to-date]
  (-> (IRODSGenQueryBuilder. true false true nil)
      add-data-object-selects
      (.addSelectAsGenQueryValue RodsGenQueryEnum/COL_META_DATA_ATTR_NAME)
      (.addSelectAsGenQueryValue RodsGenQueryEnum/COL_META_DATA_ATTR_VALUE)
      (.addSelectAsGenQueryValue RodsGenQueryEnum/COL_META_DATA_ATTR_UNITS)
      (add-modify-time-condition QueryConditionOperators/GREATER_THAN_OR_EQUAL_TO from-date)
      (add-modify-time-condition QueryConditionOperators/LESS_THAN_OR_EQUAL_TO to-date)
      (add-replica-number-condition QueryConditionOperators/EQUAL 0)
      (.addOrderByGenQueryField RodsGenQueryEnum/COL_COLL_NAME GenQueryOrderByField$OrderByType/ASC)
      (.addOrderByGenQueryField RodsGenQueryEnum/COL_DATA_NAME GenQueryOrderByField$OrderByType/ASC)))

;; I haven't quite figured out why yet, but the second query doesn't return the expected results when the first query is
;; included in the list of queries. We don't expect to have files directly in the root directory at this time, so checks
;; for them will be omitted.
(defn- build-base-data-object-listing-queries* [this from-date to-date root]
  [#_(-> (build-base-data-object-listing-query this from-date to-date)
         (add-coll-name-condition QueryConditionOperators/EQUAL root))
   (-> (build-base-data-object-listing-query this from-date to-date)
       (add-coll-name-condition QueryConditionOperators/LIKE (str root "/%")))])

(defn- build-base-data-object-listing-queries [this from-date to-date]
  (mapcat #(build-base-data-object-listing-queries* this from-date to-date %) (get-roots this)))

;; FIXME: inclusions and exclusions won't work if the results can span multiple zones.
(defn- build-data-object-listing-queries
  "Builds and returns a query to list data objects. Note that the query builder is returned rather
   than the query itself. This allows limits to be determined dynamically when multiple queries are
   combined."
  [this from-date to-date & [{:keys [exclusions inclusions]}]]
  (for [builder (build-base-data-object-listing-queries this from-date to-date)]
    (-> (add-attribute-name-condition builder QueryConditionOperators/EQUAL (get-uuid-attr this))
        (add-exclusions RodsGenQueryEnum/COL_D_DATA_ID (mapv str exclusions))
        (add-inclusions RodsGenQueryEnum/COL_D_DATA_ID (mapv str inclusions)))))

(defn- file-data-one-object-from-row [this row]
  (FileDataOneObject.
   (.getPublicationContext this)
   (.getIrodsAccount this)
   (identifier-from-string (.getColumn row (.getName RodsGenQueryEnum/COL_META_DATA_ATTR_VALUE)))
   (epoch-to-date (.getColumn row (.getName RodsGenQueryEnum/COL_D_MODIFY_TIME)))
   (DataAOHelper/buildDomainFromResultSetRow row)))

(defn- build-custom-format-listing-queries [this from-date to-date]
  (for [builder (build-base-data-object-listing-queries this from-date to-date)]
    (-> (add-attribute-name-condition builder QueryConditionOperators/EQUAL (get-format-id-attr this))
        (.exportIRODSQueryFromBuilder (get-query-page-length this)))))

;; FIXME: this won't work if the results can span multiple zones.
(defn- list-custom-format-ids [this from-date to-date]
  (->> (build-custom-format-listing-queries this from-date to-date)
       (lazy-gen-queries this)
       (mapv #(.getId (DataAOHelper/buildDomainFromResultSetRow %)))))

(defn- build-paths-with-format-listing-queries [this from-date to-date format]
  (for [builder (build-base-data-object-listing-queries this from-date to-date)]
    (-> (add-attribute-name-condition builder QueryConditionOperators/EQUAL (get-format-id-attr this))
        (add-attribute-value-condition QueryConditionOperators/EQUAL format)
        (.exportIRODSQueryFromBuilder (get-query-page-length this)))))

;; FIXME: this won't work if the results can span multiple zones.
(defn- list-ids-with-format [this from-date to-date format]
  (->> (build-paths-with-format-listing-queries this from-date to-date format)
       (lazy-gen-queries this)
       (mapv #(.getId (DataAOHelper/buildDomainFromResultSetRow %)))))

(defn- data-one-object-list-response-from-result-set [this rs start-index count]
  (if (= count 0)
    (DataOneObjectListResponse. [] (.getTotalRecords rs) start-index)
    (let [elements (mapv (partial file-data-one-object-from-row this) (.getResults rs))]
      (DataOneObjectListResponse. elements (.getTotalRecords rs) start-index))))

(defn- list-default-format-data-objects [this from-date to-date start-index count]
  (let [exclusions     (list-custom-format-ids this from-date to-date)
        query-builders (build-data-object-listing-queries this from-date to-date {:exclusions exclusions})
        rs             (gen-queries this query-builders start-index count)]
    (data-one-object-list-response-from-result-set this rs start-index count)))

(defn- list-exposed-data-objects-with-format [this from-date to-date format start-index count]
  (let [inclusions (list-ids-with-format this from-date to-date format)]
    (if (seq inclusions)
      (let [query-builders (build-data-object-listing-queries this from-date to-date {:inclusions inclusions})
            rs             (gen-queries this query-builders start-index count)]
        (data-one-object-list-response-from-result-set this rs start-index count))
      (DataOneObjectListResponse. [] 0 0))))

(defn- list-exposed-data-objects
  ([this from-date to-date start-index count]
   (let [query-builders (build-data-object-listing-queries this from-date to-date)
         rs             (gen-queries this query-builders start-index count)]
     (data-one-object-list-response-from-result-set this rs start-index count)))
  ([this from-date to-date format-id start-index count]
   (condp = (some-> format-id .getValue)
     nil            (list-exposed-data-objects this from-date to-date start-index count)
     default-format (list-default-format-data-objects this from-date to-date start-index count)
     (list-exposed-data-objects-with-format this from-date to-date (.getValue format-id) start-index count))))

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
        limit  (min (or limit default-limit) max-limit)]
    (try
      (list-exposed-data-objects this from-date to-date format-id offset limit)
      (catch Throwable t
        (log/error t)
        (throw t)))))

(defn -getLastModifiedDate [this path]
  (get-last-modified-date this path))

(defn -getFormat [this path]
  (or (get-format this path) default-format))
