(ns org.cyverse.dataone.ClojurePidServiceFactory
  (:gen-class :extends org.irods.jargon.pid.pidservice.AbstractDataOnePidFactory))

(defn -instance [this publication-context irods-account]
  (org.cyverse.dataone.ClojurePidService. irods-account publication-context))
