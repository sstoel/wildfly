/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.connector.services.workmanager.transport;

import org.jboss.jca.core.spi.workmanager.Address;
import org.wildfly.clustering.dispatcher.Command;

/**
 * Equivalent to {@link org.jboss.jca.core.workmanager.transport.remote.jgroups.JGroupsTransport#deltaDoWorkRejected(java.util.Map)}.
 * @author Paul Ferraro
 */
public class DeltaDoWorkRejectedCommand implements Command<Void, CommandDispatcherTransport> {
    private static final long serialVersionUID = 7864134087221121821L;

    private final Address address;

    public DeltaDoWorkRejectedCommand(Address address) {
        this.address = address;
    }

    @Override
    public Void execute(CommandDispatcherTransport transport) {
        transport.localDeltaDoWorkRejected(this.address);
        return null;
    }
}
