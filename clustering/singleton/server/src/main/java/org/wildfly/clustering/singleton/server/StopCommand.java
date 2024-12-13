/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.singleton.server;

import org.wildfly.clustering.server.dispatcher.Command;
import org.wildfly.clustering.server.manager.Service;

/**
 * Command to stop a singleton service.
 * @author Paul Ferraro
 */
public enum StopCommand implements Command<Void, Service, RuntimeException> {
    INSTANCE;

    @Override
    public Void execute(Service service) {
        service.stop();
        return null;
    }
}
