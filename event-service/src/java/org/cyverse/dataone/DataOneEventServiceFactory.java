package org.cyverse.dataone;

import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.dataone.events.AbstractDataOneEventServiceAO;
import org.irods.jargon.dataone.events.AbstractDataOneEventServiceFactory;
import org.irods.jargon.dataone.plugin.PublicationContext;

public class DataOneEventServiceFactory extends AbstractDataOneEventServiceFactory {

    private AbstractDataOneEventServiceAO eventService = null;

    public AbstractDataOneEventServiceAO instance(PublicationContext publicationContext, IRODSAccount irodsAccount) {
        if (eventService == null) {
            eventService = new DataOneEventService(irodsAccount, publicationContext);
        }
        return eventService;
    }
}
