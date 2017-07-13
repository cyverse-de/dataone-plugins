(ns org.cyverse.dataone.ClojurePidService
  (:import [org.irods.jargon.core.exception FileNotFoundException]
           [org.irods.jargon.core.query AVUQueryElement AVUQueryElement$AVUQueryPart
            CollectionAndDataObjectListingEntry$ObjectType QueryConditionOperators]
           [org.irods.jargon.dataone.model CollectionDataOneObject]
           [org.dataone.service.types.v1 Identifier])
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [metadata-client.core :as c])
  (:gen-class :extends org.irods.jargon.dataone.pidservice.AbstractDataOnePidServiceAO
              :init init
              :state state
              :constructors {[org.irods.jargon.core.connection.IRODSAccount
                              org.irods.jargon.dataone.plugin.PublicationContext]
                             [org.irods.jargon.core.connection.IRODSAccount
                              org.irods.jargon.dataone.plugin.PublicationContext]}))

(def ^:private default-uuid-attr "ipc_UUID")
(def ^:private default-pid-attr "Identifier")
(def ^:private default-metadata-base "http://metadata:60000")
(def ^:private default-metadata-user "dataone")

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

(defn- get-metadata-client [this]
  (c/new-metadata-client (get-metadata-base this)))

(defn- valid-pid-attr? [this {:keys [attr value]}]
  (and (= attr (get-dataone-pid-attr this))
       (not (string/blank? value))))

(defn- uuid->pid [this uuid]
  (->> (:avus (c/list-avus (get-metadata-client this) (get-metadata-user this) "folder" uuid))
       (filter (partial valid-pid-attr? this))
       first
       :value))

(defn- pid->uuid [this pid]
  (->> (:avus (c/find-avus (get-metadata-client this) (get-metadata-user this)
                           {:target-type "folder" :attribute (get-dataone-pid-attr this) :value pid}))
       (remove (comp string/blank? :value))
       first
       :target_id))

(defn- attr-equals [attr]
  (AVUQueryElement/instanceForValueQuery AVUQueryElement$AVUQueryPart/ATTRIBUTE
                                         QueryConditionOperators/EQUAL
                                         attr))

(defn- value-equals [value]
  (AVUQueryElement/instanceForValueQuery AVUQueryElement$AVUQueryPart/VALUE
                                         QueryConditionOperators/EQUAL
                                         value))

(defn- get-collection-ao [this]
  (.getCollectionAO (.. this getPublicationContext getIrodsAccessObjectFactory)
                    (.getIrodsAccount this)))

(defn- get-file-system-ao [this]
  (.getIRODSFileSystemAO (.. this getPublicationContext getIrodsAccessObjectFactory)
                         (.getIrodsAccount this)))

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

(defn- get-dataone-pid [this path]
  (when-let [uuid (get-collection-uuid this path)]
    (uuid->pid this uuid)))

(defn- get-dataone-collection [this pid]
  (let [collection-ao (get-collection-ao this)
        uuid          (pid->uuid this pid)
        query         [(attr-equals (get-uuid-attr this)) (value-equals pid)]]
    (first (.findDomainByMetadataQuery collection-ao query))))

(defn -init [irods-account publication-context]
  [[irods-account publication-context] {}])

(defn -getIdentifier [this path]
  (when-let [pid (get-dataone-pid this path)]
    (doto (Identifier.)
      (.setValue pid))))

(defn -getObject [this pid]
  (when-let [collection (get-dataone-collection this (.getValue pid))]
    (CollectionDataOneObject. (.getPublicationContext this) (.getIrodsAccount this) pid collection)))
