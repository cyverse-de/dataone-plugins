(ns org.cyverse.dataone.ClojureEventServiceFactory
  (:refer-clojure)
  (:gen-class :extends org.irods.jargon.dataone.events.AbstractDataOneEventServiceFactory))

(defn -instance [this publication-context irods-account]
  (org.cyverse.dataone.ClojureEventService. irods-account publication-context))
