(ns org.cyverse.dataone.ClojureEventServiceFactory
  (:gen-class :implements [org.irods.jargon.dataone.events.DataOneEventServiceFactory]))

(defn -instance [this publication-context irods-account]
  (org.cyverse.dataone.ClojureEventService. irods-account publication-context))
