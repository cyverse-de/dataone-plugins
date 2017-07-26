package org.cyverse.dataone;

import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.dataone.reposervice.AbstractDataOneRepoServiceFactory;
import org.irods.jargon.dataone.reposervice.AbstractDataOneRepoServiceAO;
import org.irods.jargon.dataone.plugin.PublicationContext;

public class ClojureRepoServiceFactory extends AbstractDataOneRepoServiceFactory {

    public AbstractDataOneRepoServiceAO instance(PublicationContext publicationContext, IRODSAccount irodsAccount) {
        return new ClojureRepoService(irodsAccount, publicationContext);
    }
}
