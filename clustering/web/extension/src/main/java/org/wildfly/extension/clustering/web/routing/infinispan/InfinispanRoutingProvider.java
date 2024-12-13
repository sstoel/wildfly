/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.clustering.web.routing.infinispan;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.wildfly.clustering.infinispan.service.CacheServiceInstallerFactory;
import org.wildfly.clustering.infinispan.service.TemplateConfigurationServiceInstallerFactory;
import org.wildfly.clustering.server.service.BinaryServiceConfiguration;
import org.wildfly.clustering.server.service.ClusteringServiceDescriptor;
import org.wildfly.clustering.server.service.FilteredBinaryServiceInstallerProvider;
import org.wildfly.clustering.session.cache.affinity.SessionAffinityRegistryEntry;
import org.wildfly.common.iteration.CompositeIterable;
import org.wildfly.extension.clustering.web.routing.LocalRoutingProvider;
import org.wildfly.subsystem.service.ServiceDependency;
import org.wildfly.subsystem.service.ServiceInstaller;

/**
 * Routing provider backed by an Infinispan cache.
 * @author Paul Ferraro
 */
public class InfinispanRoutingProvider extends LocalRoutingProvider {

    private final BinaryServiceConfiguration configuration;
    private final Consumer<ConfigurationBuilder> configurator;

    public InfinispanRoutingProvider(BinaryServiceConfiguration configuration, Consumer<ConfigurationBuilder> configurator) {
        this.configuration = configuration;
        this.configurator = configurator;
    }

    @Override
    public Iterable<ServiceInstaller> getServiceInstallers(CapabilityServiceSupport support, String serverName, ServiceDependency<String> route) {
        BinaryServiceConfiguration serverConfiguration = this.configuration.withChildName(serverName);
        List<ServiceInstaller> installers = new LinkedList<>();

        installers.add(ServiceInstaller.builder(route.map(SessionAffinityRegistryEntry::new))
                .provides(serverConfiguration.resolveServiceName(ClusteringServiceDescriptor.REGISTRY_ENTRY))
                .build());

        installers.add(new TemplateConfigurationServiceInstallerFactory(this.configurator).apply(this.configuration, serverConfiguration));
        installers.add(CacheServiceInstallerFactory.INSTANCE.apply(serverConfiguration));

        new FilteredBinaryServiceInstallerProvider(Set.of(ClusteringServiceDescriptor.REGISTRY, ClusteringServiceDescriptor.REGISTRY_FACTORY)).apply(support, serverConfiguration).forEach(installers::add);

        return new CompositeIterable<>(List.of(installers, super.getServiceInstallers(support, serverName, route)));
    }
}
