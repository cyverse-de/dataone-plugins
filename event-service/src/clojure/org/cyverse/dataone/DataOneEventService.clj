(ns org.cyverse.dataone.DataOneEventService
  (:refer-clojure)
  (:import [com.mchange.v2.c3p0 ComboPooledDataSource])
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [honeysql.core :as sql]
            [honeysql.helpers :as h])
  (:gen-class :extends org.irods.jargon.dataone.events.AbstractDataOneEventServiceAO
              :init init
              :constructors {[org.irods.jargon.core.connection.IRODSAccount
                              org.irods.jargon.dataone.plugin.PublicationContext]
                             [org.irods.jargon.core.connection.IRODSAccount
                              org.irods.jargon.dataone.plugin.PublicationContext]}
              :state state))

;; Default configuration settings.

(def ^:private default-jdbc-url "jdbc:pgsql://localhost:5432/dataone_events")
(def ^:private default-jdbc-user "de")
(def ^:private default-jdbc-password "notprod")
(def ^:private default-jdbc-driver "com.impossibl.postgres.jdbc.PGDriver")

;; Functions to retrieve configuration settings.

(defn- get-additional-properties [publication-context]
  (.getAdditionalProperties publication-context))

(defn- get-property [publication-context name default]
  (.getProperty (get-additional-properties publication-context) name default))

(defn- get-jdbc-url [publication-context]
  (get-property publication-context "jdbc.url" default-jdbc-url))

(defn- get-jdbc-user [publication-context]
  (get-property publication-context "jdbc.user" default-jdbc-user))

(defn- get-jdbc-password [publication-context]
  (get-property publication-context "jdbc.password" default-jdbc-password))

(defn- get-jdbc-driver [publication-context]
  (get-property publication-context "jdbc.driver" default-jdbc-driver))

(defn- create-db-pool [publication-context]
  {:datasource
   (doto (ComboPooledDataSource.)
     (.setDriverClass (get-jdbc-driver publication-context))
     (.setJdbcUrl (get-jdbc-url publication-context))
     (.setUser (get-jdbc-user publication-context))
     (.setPassword (get-jdbc-password publication-context))
     (.setMaxIdleTimeExcessConnections (* 30 60))
     (.setMaxIdleTime (* 3 60 60)))})

(defn- db-connection [this]
  (:db-pool (.state this)))

(defn add-query-filters [query start end event-type pid]
  (-> query
      (h/merge-where (when start [:<= start :date_logged]))
      (h/merge-where (when end [:<= :date_logged end]))
      (h/merge-where (when event-type [:= :event (.getInternalEvent event-type)]))
      (h/merge-where (when pid [:= :permanent_id pid]))))

(defn count-logs-query [this start end event-type pid]
  (-> (h/select :%count.*)
      (h/from :event_log)
      (add-query-filters start end event-type pid)
      sql/format))

(defn get-logs-query [this start end event-type pid offset limit]
  (-> (h/select :*)
      (h/from :event_log)
      (add-query-filters start end event-type pid)
      sql/format))

(defn -init [irods-account publication-context]
  [[irods-account publication-context] {:db-pool (create-db-pool publication-context)}])

(defn -getLogs [this start end event-type pid offset limit]
  (println (count-logs this start end event-type pid))
  (println (get-logs this start end event-type pid offset limit)))

(defn -recordEvent [this event-data])
