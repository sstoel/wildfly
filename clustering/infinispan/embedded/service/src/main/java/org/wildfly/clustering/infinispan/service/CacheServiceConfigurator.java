/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.infinispan.service;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.cache.impl.AbstractDelegatingAdvancedCache;
import org.infinispan.manager.EmbeddedCacheManager;
import org.jboss.as.clustering.controller.CapabilityServiceConfigurator;
import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.clustering.service.AsyncServiceConfigurator;
import org.wildfly.clustering.service.CompositeDependency;
import org.wildfly.clustering.service.Dependency;
import org.wildfly.clustering.service.FunctionalService;
import org.wildfly.clustering.service.ServiceConfigurator;
import org.wildfly.clustering.service.ServiceDependency;
import org.wildfly.clustering.service.ServiceSupplierDependency;
import org.wildfly.clustering.service.SimpleServiceNameProvider;
import org.wildfly.clustering.service.SupplierDependency;

/**
 * Service that provides a cache and handles its lifecycle
 * @author Paul Ferraro
 * @param <K> the cache key type
 * @param <V> the cache value type
 */
public class CacheServiceConfigurator<K, V> extends SimpleServiceNameProvider implements CapabilityServiceConfigurator, Supplier<Cache<K, V>>, Consumer<Cache<K, V>> {

    private final String containerName;
    private final String cacheName;

    private volatile SupplierDependency<EmbeddedCacheManager> container;
    private volatile Dependency configuration;

    public CacheServiceConfigurator(ServiceName name, String containerName, String cacheName) {
        super(name);
        this.containerName = containerName;
        this.cacheName = cacheName;
    }

    @Override
    public Cache<K, V> get() {
        Cache<K, V> cache = this.container.get().getCache(this.cacheName);
        cache.start();
        return cache;
    }

    @Override
    public void accept(Cache<K, V> cache) {
        cache.stop();
    }

    @Override
    public ServiceConfigurator configure(CapabilityServiceSupport support) {
        this.container = new ServiceSupplierDependency<>(InfinispanRequirement.CONTAINER.getServiceName(support, this.containerName));
        this.configuration = new ServiceDependency(InfinispanCacheRequirement.CONFIGURATION.getServiceName(support, this.containerName, this.cacheName));
        return this;
    }

    @Override
    public final ServiceBuilder<?> build(ServiceTarget target) {
        ServiceBuilder<?> builder = new AsyncServiceConfigurator(this.getServiceName()).build(target);
        Consumer<Cache<K, V>> cache = new CompositeDependency(this.configuration, this.container).register(builder).provides(this.getServiceName());
        Service service = new FunctionalService<>(cache, ManagedCache::new, this, this);
        return builder.setInstance(service).setInitialMode(ServiceController.Mode.ON_DEMAND);
    }

    private static class ManagedCache<K, V> extends AbstractDelegatingAdvancedCache<K, V> {

        ManagedCache(Cache<K, V> cache) {
            this(cache.getAdvancedCache());
        }

        private ManagedCache(AdvancedCache<K, V> cache) {
            super(cache.getAdvancedCache());
        }

        @SuppressWarnings("unchecked")
        @Override
        public AdvancedCache rewrap(AdvancedCache delegate) {
            return new ManagedCache<>(delegate);
        }

        @Override
        public void start() {
            // No-op.  Lifecycle managed by container.
        }

        @Override
        public void stop() {
            // No-op.  Lifecycle managed by container.
        }
    }
}