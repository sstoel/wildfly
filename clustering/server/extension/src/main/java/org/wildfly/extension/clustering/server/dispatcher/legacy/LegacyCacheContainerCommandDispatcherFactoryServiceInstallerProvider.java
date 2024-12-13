/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.clustering.server.dispatcher.legacy;

import java.util.Map;

import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.kohsuke.MetaInfServices;
import org.wildfly.clustering.server.infinispan.dispatcher.CacheContainerCommandDispatcherFactory;
import org.wildfly.clustering.server.service.CacheContainerServiceInstallerProvider;
import org.wildfly.extension.clustering.server.UnaryServiceInstallerProvider;
import org.wildfly.subsystem.service.ServiceInstaller;

/**
 * @author Paul Ferraro
 */
@Deprecated
@MetaInfServices(CacheContainerServiceInstallerProvider.class)
public class LegacyCacheContainerCommandDispatcherFactoryServiceInstallerProvider implements CacheContainerServiceInstallerProvider {

    private final UnaryServiceInstallerProvider<org.wildfly.clustering.dispatcher.CommandDispatcherFactory> provider = new LegacyCommandDispatcherFactoryServiceInstallerProvider(new LegacyCommandDispatcherFactoryServiceInstallerFactory<>(CacheContainerCommandDispatcherFactory.class, LegacyCacheContainerCommandDispatcherFactory::wrap));

    @Override
    public Iterable<ServiceInstaller> apply(CapabilityServiceSupport support, Map.Entry<String, String> entry) {
        return this.provider.apply(support, entry.getKey());
    }
}
