(ns org.cyverse.dataone.ClojurePidService
  (:refer-clojure)
  (:import [org.irods.jargon.core.exception FileNotFoundException]
           [org.irods.jargon.core.query AVUQueryElement AVUQueryElement$AVUQueryPart
            CollectionAndDataObjectListingEntry$ObjectType QueryConditionOperators]
           [org.irods.jargon.dataone.model FileDataOneObject]
           [org.dataone.service.types.v1 Identifier])
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log])
  (:gen-class :extends org.irods.jargon.dataone.pidservice.AbstractDataOnePidServiceAO
              :init init
              :state state
              :constructors {[org.irods.jargon.core.connection.IRODSAccount
                              org.irods.jargon.dataone.plugin.PublicationContext]
                             [org.irods.jargon.core.connection.IRODSAccount
                              org.irods.jargon.dataone.plugin.PublicationContext]}))

(def ^:private default-uuid-attr "ipc_UUID")

(defn- get-additional-properties [this]
  (.. this getPublicationContext getAdditionalProperties))

(defn- get-property [this name default]
  (.getProperty (get-additional-properties this) name default))

(defn- get-uuid-attr [this]
  (get-property this "cyverse.avu.uuid-attr" default-uuid-attr))

(defn- attr-equals [attr]
  (AVUQueryElement/instanceForValueQuery AVUQueryElement$AVUQueryPart/ATTRIBUTE
                                         QueryConditionOperators/EQUAL
                                         attr))

(defn- value-equals [value]
  (AVUQueryElement/instanceForValueQuery AVUQueryElement$AVUQueryPart/VALUE
                                         QueryConditionOperators/EQUAL
                                         value))

(defn- get-data-object-ao [this]
  (.getDataObjectAO (.. this getPublicationContext getIrodsAccessObjectFactory)
                    (.getIrodsAccount this)))

(defn- get-file-system-ao [this]
  (.getIRODSFileSystemAO (.. this getPublicationContext getIrodsAccessObjectFactory)
                         (.getIrodsAccount this)))

(defn- is-file? [this path]
  (try
    (let [stat (.getObjStat (get-file-system-ao this) path)]
      (= (.getObjectType stat)
         (CollectionAndDataObjectListingEntry$ObjectType/DATA_OBJECT)))
    (catch FileNotFoundException _ false)))

(defn- get-dataone-pid [this path]
  (when (is-file? this path)
    (let [data-object-ao (get-data-object-ao this)
          query          [(attr-equals (get-uuid-attr this))]]
      (some-> (first (.findMetadataValuesForDataObjectUsingAVUQuery data-object-ao query path))
              (.getAvuValue)))))

(defn- get-dataone-file [this uuid]
  (let [data-object-ao (get-data-object-ao this)
        query          [(attr-equals (get-uuid-attr this)) (value-equals uuid)]]
    (first (.findDomainByMetadataQuery data-object-ao query))))

(defn -init [irods-account publication-context]
  [[irods-account publication-context] {}])

(defn -getIdentifier [this path]
  (when-let [pid (get-dataone-pid this path)]
    (doto (Identifier.)
      (.setValue pid))))

(defn -getObject [this pid]
  (when-let [file (get-dataone-file this (.getValue pid))]
    (FileDataOneObject. (.getPublicationContext this) (.getIrodsAccount this) pid file)))
