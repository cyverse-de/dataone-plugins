(ns org.cyverse.dataone.ClojurePidService
  (:import [org.irods.jargon.core.pub.domain DataObject]
           [org.dataone.service.types.v1 Identifier])
  (:require [clojure.tools.logging :as log])
  (:gen-class :extends org.irods.jargon.pid.pidservice.AbstractUniqueIdAO
              :init init
              :state state
              :constructors {[org.irods.jargon.core.connection.IRODSAccount
                              org.irods.jargon.dataone.configuration.PublicationContext]
                             [org.irods.jargon.core.connection.IRODSAccount
                              org.irods.jargon.dataone.configuration.PublicationContext]}))

(defn -init [irods-account publication-context]
  [[irods-account publication-context] {}])

(defn -getIdentifierFromDataObject [this data-object]
  (log/info "getIdentifierFromDataObject()")
  (doto (Identifier.)
    (.setValue (str "http://handle/dummy/" (System/currentTimeMillis)))))

(defn -getDataObjectFromIdentifier [this identifier]
  (log/info "getDataObjectFromIdentifier()")
  (DataObject.))
