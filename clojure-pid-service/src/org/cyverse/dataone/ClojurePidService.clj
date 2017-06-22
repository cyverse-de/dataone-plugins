(ns org.cyverse.dataone.ClojurePidService
  (:import [org.irods.jargon.core.pub.domain DataObject]
           [org.irods.jargon.core.query AVUQueryElement AVUQueryElement$AVUQueryPart QueryConditionOperators]
           [org.dataone.service.types.v1 Identifier])
  (:require [clojure.tools.logging :as log])
  (:gen-class :extends org.irods.jargon.pid.pidservice.AbstractUniqueIdAO
              :init init
              :state state
              :constructors {[org.irods.jargon.core.connection.IRODSAccount
                              org.irods.jargon.dataone.configuration.PublicationContext]
                             [org.irods.jargon.core.connection.IRODSAccount
                              org.irods.jargon.dataone.configuration.PublicationContext]}))

(def ^:private default-pid-attr "dataone-pid")

(defn- get-additional-properties [this]
  (.. this getPublicationContext getAdditionalProperties))

(defn- get-dataone-pid-attr [this]
  (.getProperty (get-additional-properties this) "irods.dataone.pid.attr" default-pid-attr))

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

(defn- get-dataone-pid [this data-object]
  (let [data-object-ao (get-data-object-ao this)
        query          [(attr-equals (get-dataone-pid-attr this))]
        path           (.getAbsolutePath data-object)]
    (some-> (.findMetadataValuesForDataObjectUsingAVUQuery data-object-ao query path)
            first
            (.getAvuValue))))

(defn- get-dataone-object [this id]
  (let [data-object-ao (get-data-object-ao this)
        query          [(attr-equals (get-dataone-pid-attr this)) (value-equals id)]]
    (some-> (.findDomainByMetadataQuery data-object-ao query)
            first)))

(defn -init [irods-account publication-context]
  [[irods-account publication-context] {}])

(defn -getIdentifierFromDataObject [this data-object]
  (when-let [pid (get-dataone-pid this data-object)]
    (doto (Identifier.)
      (.setValue pid))))

(defn -getDataObjectFromIdentifier [this pid]
  (get-dataone-object this (.getValue pid)))
