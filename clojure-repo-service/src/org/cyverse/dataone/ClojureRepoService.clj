(ns org.cyverse.dataone.ClojureRepoService
  (:refer-clojure)
  (:import [org.dataone.service.types.v1 Identifier]
           [org.irods.jargon.core.exception FileNotFoundException]
           [org.irods.jargon.core.query AVUQueryElement AVUQueryElement$AVUQueryPart
            CollectionAndDataObjectListingEntry$ObjectType QueryConditionOperators]
           [org.irods.jargon.dataone.model CollectionDataOneObject DataOneObjectListResponse]
           [java.util Date])
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [metadata-client.core :as c])
  (:gen-class :extends org.irods.jargon.dataone.reposervice.AbstractDataOneRepoServiceAO
              :init init
              :constructors {[org.irods.jargon.core.connection.IRODSAccount
                              org.irods.jargon.dataone.plugin.PublicationContext]
                             [org.irods.jargon.core.connection.IRODSAccount
                              org.irods.jargon.dataone.plugin.PublicationContext]}))

;; Default configuration settings.

(def ^:private default-uuid-attr "ipc_UUID")
(def ^:private default-pid-attr "Identifier")
(def ^:private default-metadata-base "http://metadata:60000")
(def ^:private default-metadata-user "dataone")
(def ^:private default-page-length "50")
(def ^:private default-format "application/octet-stream")

;; Functions to retrieve configuration settings.

(defn- get-additional-properties [this]
  (.. this getPublicationContext getAdditionalProperties))

(defn- get-property [this name default]
  (.getProperty (get-additional-properties this) name default))

(defn- get-uuid-attr [this]
  (get-property this "cyverse.avu.uuid-attr" default-uuid-attr))

(defn- get-dataone-pid-attr [this]
  (get-property this "cyverse.metadata.pid-attr" default-pid-attr))

(defn- get-metadata-base [this]
  (get-property this "cyverse.metadata.base" default-metadata-base))

(defn- get-metadata-user [this]
  (get-property this "cyverse.metadata.user" default-metadata-user))

(defn- get-query-page-length [this]
  (-> (get-property this "irods.dataone.query-page-length" default-page-length)
      Integer/parseInt))

(defn- get-metadata-client [this]
  (c/new-metadata-client (get-metadata-base this)))

;; General jargon convenience functions.

(defn- get-collection-ao [this]
  (.getCollectionAO (.. this getPublicationContext getIrodsAccessObjectFactory)
                    (.getIrodsAccount this)))

(defn- get-file-system-ao [this]
  (.getIRODSFileSystemAO (.. this getPublicationContext getIrodsAccessObjectFactory)
                         (.getIrodsAccount this)))

;; Common query conditions.

(defn- attr-equals [attr]
  (AVUQueryElement/instanceForValueQuery AVUQueryElement$AVUQueryPart/ATTRIBUTE
                                         QueryConditionOperators/EQUAL
                                         attr))

(defn- value-equals [value]
  (AVUQueryElement/instanceForValueQuery AVUQueryElement$AVUQueryPart/VALUE
                                         QueryConditionOperators/EQUAL
                                         value))

;; Functions to retrieve the list of exposed identifiers.

(defn- list-exposed-identifiers [this]
  (->> (c/find-avus (get-metadata-client this) (get-metadata-user this)
                    {:target-type "folder" :attribute (get-dataone-pid-attr this)})
       :avus
       (remove (comp string/blank? :value))
       vec))

;; Functions to retrieve the list of exposed data objects.

(defn- build-pred [pred l]
  (if (nil? l)
    (constantly true)
    (fn [r] (pred l r))))

(defn- get-time [d]
  (when d
    (.getTime d)))

(defn- avu-matches? [from-date to-date avu]
  ((every-pred (comp (build-pred <= (get-time from-date)) :modified_on)
               (comp (build-pred >= (get-time to-date)) :modified_on))
   avu))

(defn- list-matching-identifiers [this from-date to-date]
  (some->> (list-exposed-identifiers this)
           (filter (partial avu-matches? from-date to-date))
           vec))

(defn- collection-for-pid-avu [this {uuid :target_id pid :value}]
  (let [collection-ao (get-collection-ao this)
        query         [(attr-equals (get-uuid-attr this)) (value-equals uuid)]]
    (when-let [collection (first (.findDomainByMetadataQuery collection-ao query))]
      (CollectionDataOneObject. (.getPublicationContext this)
                                (.getIrodsAccount this)
                                (doto (Identifier.) (.setValue pid))
                                collection))))

(defn- apply-range [avus offset count]
  (drop offset (if count (take count avus) avus)))

;; TODO: modify this so that it does all of the collection lookups at once if it's too slow.
(defn- list-exposed-collections [this from-date to-date start-index count]
  (let [offset (or start-index 0)
        avus   (list-matching-identifiers this from-date to-date)]
    (DataOneObjectListResponse. (mapv (partial collection-for-pid-avu this)
                                      (apply-range avus offset count))
                                (count avus)
                                offset)))

(defn- is-collection? [this path]
  (try
    (let [stat (.getObjStat (get-file-system-ao this) path)]
      (= (.getObjectType stat)
         (CollectionAndDataObjectListingEntry$ObjectType/COLLECTION)))
    (catch FileNotFoundException e false)))

(defn- get-collection-uuid [this path]
  (when (is-collection? this path)
    (let [collection-ao (get-collection-ao this)
          query         [(attr-equals (get-uuid-attr this))]]
      (some-> (.findMetadataValuesByMetadataQueryForCollection collection-ao query path)
              first
              (.getAvuValue)))))

(defn- valid-pid-attr? [this {:keys [attr value]}]
  (and (= attr (get-dataone-pid-attr this))
       (not (string/blank? value))))

(defn- uuid->avu [this uuid]
  (->> (:avus (c/list-avus (get-metadata-client this) (get-metadata-user this) "folder" uuid))
       (filter (partial valid-pid-attr? this))
       first))

;; Class method implementations.

(defn -init [irods-account publication-context]
  [[irods-account publication-context] {}])

(defn -getListOfDataoneExposedIdentifiers [this]
  (mapv (fn [s] (doto (Identifier.) (.setValue (:value s))))
        (list-exposed-identifiers this)))

(defn -getExposedObjects [this from-date to-date format-id _ start-index count]
  (if (or (nil? format-id) (= (.getValue format-id) default-format))
    (list-exposed-collections this from-date to-date start-index count)
    (DataOneObjectListResponse. [] 0 (or start-index 0))))

(defn -getLastModifiedDate [this path]
  (some->> (get-collection-uuid this path)
           (uuid->avu this)
           :modified_on
           (Date.)))

(defn -getFormat [_ _]
  default-format)
