(ns org.cyverse.dataone.ClojureRepoService
  (:import [org.dataone.service.types.v1 Identifier]
           [org.irods.jargon.dataone.reposervice DataObjectListResponse])
  (:require [clojure.tools.logging :as log])
  (:gen-class :extends org.irods.jargon.dataone.reposervice.AbstractDataOneRepoServiceAO
              :init init
              :state state
              :constructors {[org.irods.jargon.core.connection.IRODSAccount
                              org.irods.jargon.dataone.configuration.PublicationContext]
                             [org.irods.jargon.core.connection.IRODSAccount
                              org.irods.jargon.dataone.configuration.PublicationContext]}))

(defn -init [irods-account publication-context]
  [[irods-account publication-context] {}])

(defn -getListOfDataOneExposedIdentifiers [this]
  (log/info "getListOfDataOneExposedIdentifiers()")
  (mapv (fn [s] (doto (Identifier.) (.setValue s))) ["foo" "bar" "baz"]))

(defn -getListOfExposedDataObjects [this from-date to-date format-id replica-status start-index count]
  (log/info "getListOfExposedDataObjects()")
  (DataObjectListResponse.))
