/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.clustering.server.group;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.server.ServerEnvironment;
import org.wildfly.clustering.server.local.LocalGroup;
import org.wildfly.clustering.server.service.ClusteringServiceDescriptor;
import org.wildfly.subsystem.service.ServiceDependency;
import org.wildfly.subsystem.service.ServiceInstaller;

/**
 * @author Paul Ferraro
 */
public enum LocalGroupServiceInstallerFactory implements BiFunction<CapabilityServiceSupport, String, ServiceInstaller> {
    INSTANCE;

    @Override
    public ServiceInstaller apply(CapabilityServiceSupport support, String name) {
        Function<ServerEnvironment, LocalGroup> factory = new Function<>() {
            @Override
            public LocalGroup apply(ServerEnvironment environment) {
                return LocalGroup.of(ModelDescriptionConstants.LOCAL, environment.getNodeName());
            }
        };
        return ServiceInstaller.builder(ServiceDependency.on(ServerEnvironment.SERVICE_DESCRIPTOR).map(factory))
                .provides(support.getCapabilityServiceName(ClusteringServiceDescriptor.GROUP, name))
                .build();
    }
}
