(ns org.cyverse.dataone.ClojureRepoServiceFactory
  (:gen-class :implements [org.irods.jargon.dataone.reposervice.DataOneRepoServiceFactory]))

(defn -instance [this publication-context irods-account]
  (org.cyverse.dataone.ClojureRepoService. irods-account publication-context))
