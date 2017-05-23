(ns org.cyverse.dataone.ClojureEventService
  (:import [org.dataone.service.types.v1 Event Identifier Log]
           [org.irods.jargon.core.connection IRODSAccount]
           [org.irods.jargon.dataone.configuration PublicationContext])
  (:require [clojure.tools.logging :as log])
  (:gen-class :extends org.irods.jargon.dataone.events.AbstractEventServiceAO
              :init init
              :state state
              :constructors {[org.irods.jargon.core.connection.IRODSAccount
                              org.irods.jargon.dataone.configuration.PublicationContext]
                             [org.irods.jargon.core.connection.IRODSAccount
                              org.irods.jargon.dataone.configuration.PublicationContext]}))

(defn -init [irods-account publication-context]
  [[irods-account publication-context] {}])

(defn -getLogs [this from-date to-date event pid-filter start-idx count]
  (log/info "getLogs()")
  (Log.))

(defn -recordEvent [this event id description]
  (log/info "recordEvent()")
  (log/info (str "event: " event))
  (log/info (str "id: " id))
  (log/info (str "description: " description)))
