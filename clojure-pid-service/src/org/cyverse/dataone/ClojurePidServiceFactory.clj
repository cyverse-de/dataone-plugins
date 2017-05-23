(ns org.cyverse.dataone.ClojurePidServiceFactory
  (:gen-class :implements [org.irods.jargon.pid.pidservice.DataOnePidServiceFactory]))

(defn -instance [this publication-context irods-account]
  (org.cyverse.dataone.ClojurePidService. irods-account publication-context))
