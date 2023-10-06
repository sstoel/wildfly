/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.ejb.infinispan.network;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jboss.as.clustering.controller.CapabilityServiceConfigurator;
import org.jboss.as.controller.ServiceNameFactory;
import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.network.ClientMapping;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.clustering.group.Group;
import org.wildfly.clustering.server.service.ClusteringCacheRequirement;
import org.wildfly.clustering.service.CompositeDependency;
import org.wildfly.clustering.service.FunctionalService;
import org.wildfly.clustering.service.ServiceConfigurator;
import org.wildfly.clustering.service.ServiceSupplierDependency;
import org.wildfly.clustering.service.SimpleServiceNameProvider;
import org.wildfly.clustering.service.SupplierDependency;

/**
 * Configures a service that provides a client mappings registry entry for the local cluster member.
 * @author Paul Ferraro
 */
public class ClientMappingsRegistryEntryServiceConfigurator extends SimpleServiceNameProvider implements CapabilityServiceConfigurator, Supplier<Map.Entry<String, List<ClientMapping>>> {

    private final String containerName;
    private final String cacheName;
    private final SupplierDependency<List<ClientMapping>> clientMappings;
    private volatile SupplierDependency<Group> group;

    public ClientMappingsRegistryEntryServiceConfigurator(String containerName, String cacheName, SupplierDependency<List<ClientMapping>> clientMappings) {
        super(ServiceNameFactory.parseServiceName(ClusteringCacheRequirement.REGISTRY_ENTRY.getName()).append(containerName, cacheName));
        this.containerName = containerName;
        this.cacheName = cacheName;
        this.clientMappings = clientMappings;
    }

    @Override
    public ServiceConfigurator configure(CapabilityServiceSupport support) {
        this.group = new ServiceSupplierDependency<>(ClusteringCacheRequirement.GROUP.getServiceName(support, this.containerName, this.cacheName));
        return this;
    }

    @Override
    public ServiceBuilder<?> build(ServiceTarget target) {
        ServiceName name = this.getServiceName();
        ServiceBuilder<?> builder = target.addService(name);
        Consumer<Map.Entry<String, List<ClientMapping>>> entry = new CompositeDependency(this.group, this.clientMappings).register(builder).provides(name);
        Service service = new FunctionalService<>(entry, Function.identity(), this);
        return builder.setInstance(service).setInitialMode(ServiceController.Mode.ON_DEMAND);
    }

    @Override
    public Map.Entry<String, List<ClientMapping>> get() {
        return new ClientMappingsRegistryEntry(this.group.get().getLocalMember().getName(), this.clientMappings.get());
    }
}
