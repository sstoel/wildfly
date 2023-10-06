/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.clustering.server.provider;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.infinispan.Cache;
import org.infinispan.remoting.transport.Address;
import org.jboss.as.clustering.controller.CapabilityServiceConfigurator;
import org.jboss.as.clustering.function.Consumers;
import org.jboss.as.clustering.function.Functions;
import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.clustering.infinispan.service.InfinispanCacheRequirement;
import org.wildfly.clustering.provider.ServiceProviderRegistry;
import org.wildfly.clustering.server.dispatcher.CommandDispatcherFactory;
import org.wildfly.clustering.server.group.Group;
import org.wildfly.clustering.server.infinispan.provider.AutoCloseableServiceProviderRegistry;
import org.wildfly.clustering.server.infinispan.provider.CacheServiceProviderRegistry;
import org.wildfly.clustering.server.infinispan.provider.CacheServiceProviderRegistryConfiguration;
import org.wildfly.clustering.server.infinispan.provider.LocalServiceProviderRegistry;
import org.wildfly.clustering.server.service.ClusteringCacheRequirement;
import org.wildfly.clustering.server.service.ClusteringRequirement;
import org.wildfly.clustering.service.AsyncServiceConfigurator;
import org.wildfly.clustering.service.CompositeDependency;
import org.wildfly.clustering.service.FunctionalService;
import org.wildfly.clustering.service.ServiceConfigurator;
import org.wildfly.clustering.service.ServiceSupplierDependency;
import org.wildfly.clustering.service.SimpleServiceNameProvider;
import org.wildfly.clustering.service.SupplierDependency;

/**
 * Builds a clustered {@link ServiceProviderRegistrationFactory} service.
 * @author Paul Ferraro
 */
public class CacheServiceProviderRegistryServiceConfigurator<T> extends SimpleServiceNameProvider implements CapabilityServiceConfigurator, CacheServiceProviderRegistryConfiguration<T>, Supplier<AutoCloseableServiceProviderRegistry<T>> {

    private final String containerName;
    private final String cacheName;

    private volatile SupplierDependency<CommandDispatcherFactory> dispatcherFactory;
    private volatile SupplierDependency<Group<Address>> group;
    private volatile SupplierDependency<Cache<?, ?>> cache;

    public CacheServiceProviderRegistryServiceConfigurator(ServiceName name, String containerName, String cacheName) {
        super(name);
        this.containerName = containerName;
        this.cacheName = cacheName;
    }

    @Override
    public AutoCloseableServiceProviderRegistry<T> get() {
        Cache<?, ?> cache = this.cache.get();
        return cache.getCacheConfiguration().clustering().cacheMode().isClustered() ? new CacheServiceProviderRegistry<>(this) : new LocalServiceProviderRegistry<>(this.group.get());
    }

    @Override
    public ServiceConfigurator configure(CapabilityServiceSupport support) {
        this.cache = new ServiceSupplierDependency<>(InfinispanCacheRequirement.CACHE.getServiceName(support, this.containerName, this.cacheName));
        this.dispatcherFactory = new ServiceSupplierDependency<>(ClusteringRequirement.COMMAND_DISPATCHER_FACTORY.getServiceName(support, this.containerName));
        this.group = new ServiceSupplierDependency<>(ClusteringCacheRequirement.GROUP.getServiceName(support, this.containerName, this.cacheName));
        return this;
    }

    @Override
    public ServiceBuilder<?> build(ServiceTarget target) {
        ServiceBuilder<?> builder = new AsyncServiceConfigurator(this.getServiceName()).build(target);
        Consumer<ServiceProviderRegistry<T>> registry = new CompositeDependency(this.cache, this.dispatcherFactory, this.group).register(builder).provides(this.getServiceName());
        Service service = new FunctionalService<>(registry, Functions.identity(), this, Consumers.close());
        return builder.setInstance(service).setInitialMode(ServiceController.Mode.ON_DEMAND);
    }

    @Override
    public Object getId() {
        return this.getServiceName();
    }

    @Override
    public Group<Address> getGroup() {
        return this.group.get();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <K, V> Cache<K, V> getCache() {
        return (Cache<K, V>) this.cache.get();
    }

    @Override
    public CommandDispatcherFactory getCommandDispatcherFactory() {
        return this.dispatcherFactory.get();
    }
}
