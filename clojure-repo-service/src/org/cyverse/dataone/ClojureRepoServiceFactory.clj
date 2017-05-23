(ns org.cyverse.dataone.ClojureRepoServiceFactory
  (:gen-class :extends org.irods.jargon.dataone.reposervice.AbstractDataOneRepoFactory))

(defn -instance [this publication-context irods-account]
  (org.cyverse.dataone.ClojureRepoService. irods-account publication-context))
