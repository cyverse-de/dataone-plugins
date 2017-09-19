package org.cyverse.dataone;

import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.dataone.pidservice.AbstractDataOnePidFactory;
import org.irods.jargon.dataone.pidservice.AbstractDataOnePidServiceAO;
import org.irods.jargon.dataone.plugin.PublicationContext;

public class DataOnePidServiceFactory extends AbstractDataOnePidFactory {

    public AbstractDataOnePidServiceAO instance(PublicationContext publicationContext, IRODSAccount irodsAccount) {
        return new DataOnePidService(irodsAccount, publicationContext);
    }
}
