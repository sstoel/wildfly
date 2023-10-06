/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.connector.services.workmanager.transport;

import org.jboss.jca.core.spi.workmanager.Address;
import org.wildfly.clustering.dispatcher.Command;

/**
 * Equivalent to {@link org.jboss.jca.core.workmanager.transport.remote.jgroups.JGroupsTransport#getShortRunningFree(java.util.Map)}.
 * @author Paul Ferraro
 */
public class ShortRunningFreeCommand implements Command<Long, CommandDispatcherTransport> {
    private static final long serialVersionUID = 2200993132804378135L;

    private final Address address;

    public ShortRunningFreeCommand(Address address) {
        this.address = address;
    }

    @Override
    public Long execute(CommandDispatcherTransport transport) {
        return transport.localGetShortRunningFree(this.address);
    }
}
