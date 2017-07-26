package org.cyverse.dataone;

import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.dataone.pidservice.AbstractDataOnePidFactory;
import org.irods.jargon.dataone.pidservice.AbstractDataOnePidServiceAO;
import org.irods.jargon.dataone.plugin.PublicationContext;

public class ClojurePidServiceFactory extends AbstractDataOnePidFactory {

    public AbstractDataOnePidServiceAO instance(PublicationContext publicationContext, IRODSAccount irodsAccount) {
        return new ClojurePidService(irodsAccount, publicationContext);
    }
}
