/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.clustering.controller;

import javax.management.MBeanServer;

import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.naming.NamingStore;
import org.jboss.as.naming.service.NamingService;
import org.jboss.as.network.SocketBindingManager;
import org.wildfly.clustering.service.Requirement;

/**
 * Enumerates common requirements for clustering resources.
 * @author Paul Ferraro
 */
public enum CommonRequirement implements Requirement, ServiceNameFactoryProvider {
    ELYTRON("org.wildfly.security.elytron", Void.class),
    LOCAL_TRANSACTION_PROVIDER("org.wildfly.transactions.global-default-local-provider", Void.class),
    MBEAN_SERVER("org.wildfly.management.jmx", MBeanServer.class),
    NAMING_STORE(NamingService.CAPABILITY_NAME, NamingStore.class),
    PATH_MANAGER("org.wildfly.management.path-manager", PathManager.class),
    SOCKET_BINDING_MANAGER("org.wildfly.management.socket-binding-manager", SocketBindingManager.class),
    ;
    private final String name;
    private final Class<?> type;
    private final ServiceNameFactory factory = new RequirementServiceNameFactory(this);

    CommonRequirement(String name, Class<?> type) {
        this.name = name;
        this.type = type;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Class<?> getType() {
        return this.type;
    }

    @Override
    public ServiceNameFactory getServiceNameFactory() {
        return this.factory;
    }
}
