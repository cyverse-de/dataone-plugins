(ns org.cyverse.dataone.DataOneEventService
  (:refer-clojure)
  (:import [com.mchange.v2.c3p0 ComboPooledDataSource]
           [java.sql Timestamp]
           [java.util Date]
           [org.dataone.service.types.v1 Event Identifier Log LogEntry NodeReference Subject]
           [org.irods.jargon.core.exception JargonRuntimeException]
           [org.irods.jargon.dataone.events EventsEnum])
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

;; Database connection functions.

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

;; Conversion functions.

(defn- timestamp-from-date [date]
  (when date
    (Timestamp. (.getTime date))))

(defn- date-from-timestamp [timestamp]
  (when timestamp
    (Date. (.getTime timestamp))))

(defn- identifier-from-str [id-str]
  (when-not (string/blank? id-str)
    (doto (Identifier.)
      (.setValue id-str))))

(defn- subject-from-str [subject-str]
  (when-not (string/blank? subject-str)
    (doto (Subject.)
      (.setValue subject-str))))

(defn- event-from-str [event-str]
  (when-not (string/blank? event-str)
    (Event/valueOf event-str)))

(defn- node-reference-from-id [node-id]
  (when-not (string/blank? node-id)
    (doto (NodeReference.)
      (.setValue node-id))))

;; Implementation functions for getLogs.

(defn- add-query-filters [query start end event-type pid]
  (-> query
      (h/merge-where (when start [:<= start :date_logged]))
      (h/merge-where (when end [:<= :date_logged end]))
      (h/merge-where (when event-type [:= :event (str event-type)]))
      (h/merge-where (when pid [:= :permanent_id pid]))))

(defn- count-logs-query [this start end event-type pid]
  (-> (h/select :%count.*)
      (h/from :event_log)
      (add-query-filters start end event-type pid)
      sql/format))

(defn- get-logs-query [this start end event-type pid offset limit]
  (-> (h/select :*)
      (h/from :event_log)
      (add-query-filters start end event-type pid)
      (h/offset (when (pos? offset) offset))
      (h/limit (when (pos? limit) limit))
      sql/format))

(defn- log-entry-from-map [m]
  (doto (LogEntry.)
    (.setEntryId (str (:id m)))
    (.setIdentifier (identifier-from-str (:permanent_id m)))
    (.setIpAddress (:ip_address m))
    (.setUserAgent (:user_agent m))
    (.setSubject (subject-from-str (:subject m)))
    (.setEvent (event-from-str (:event m)))
    (.setDateLogged (date-from-timestamp (:date_logged m)))
    (.setNodeIdentifier (node-reference-from-id (:node_identifier m)))))

;; Implementation functions for recordEvent.

(defn- event-data-insertion-map [event-data]
  {:permanent_id    (.. event-data getId getValue)
   :irods_path      (.. event-data getIrodsPath)
   :ip_address      (.. event-data getIpAddress)
   :user_agent      (.. event-data getUserAgent)
   :subject         (.. event-data getSubject)
   :event           (sql/call :cast (str (EventsEnum/valueOfFromDataOne (.. event-data getEvent))) :event_type)
   :date_logged     :%now
   :node_identifier (.. event-data getNodeIdentifier)})

(defn- insert-log-entry-statement [event-data]
  (-> (h/insert-into :event_log)
      (h/values [(event-data-insertion-map event-data)])
      sql/format))

;; Method implementations.

(defn -init [irods-account publication-context]
  [[irods-account publication-context] {:db-pool (create-db-pool publication-context)}])

(defn -getLogs [this start end event-type pid offset limit]
  (try
    (jdbc/with-db-transaction [tx (db-connection this)]
      (let [start   (timestamp-from-date start)
            end     (timestamp-from-date end)
            total   (:count (first (jdbc/query tx (count-logs-query this start end event-type pid))))
            results (jdbc/query tx (get-logs-query this start end event-type pid offset limit))]
        (doto (Log.)
          (.setStart offset)
          (.setTotal total)
          (.setLogEntryList (map log-entry-from-map results)))))
    (catch Exception e
      (log/error e)
      (throw (JargonRuntimeException. e)))))

(defn -recordEvent [this event-data]
  (try
    (jdbc/with-db-transaction [tx (db-connection this)]
      (jdbc/execute! tx (insert-log-entry-statement event-data)))
    (catch Exception e
      (log/error e)
      (throw (JargonRuntimeException. e)))))
