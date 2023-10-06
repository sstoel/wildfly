/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.server.infinispan.provider;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.infinispan.Cache;
import org.infinispan.commons.CacheException;
import org.infinispan.commons.util.CloseableIterator;
import org.infinispan.context.Flag;
import org.infinispan.distribution.ch.ConsistentHash;
import org.infinispan.notifications.Listener.Observation;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryCreated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryModified;
import org.infinispan.notifications.cachelistener.annotation.TopologyChanged;
import org.infinispan.notifications.cachelistener.event.CacheEntryEvent;
import org.infinispan.notifications.cachelistener.event.TopologyChangedEvent;
import org.infinispan.remoting.transport.Address;
import org.wildfly.clustering.context.DefaultExecutorService;
import org.wildfly.clustering.context.ExecutorServiceFactory;
import org.wildfly.clustering.ee.Batch;
import org.wildfly.clustering.ee.Batcher;
import org.wildfly.clustering.ee.Invoker;
import org.wildfly.clustering.ee.infinispan.retry.RetryingInvoker;
import org.wildfly.clustering.group.Node;
import org.wildfly.clustering.infinispan.distribution.ConsistentHashLocality;
import org.wildfly.clustering.infinispan.distribution.Locality;
import org.wildfly.clustering.provider.ServiceProviderRegistration;
import org.wildfly.clustering.provider.ServiceProviderRegistration.Listener;
import org.wildfly.clustering.provider.ServiceProviderRegistry;
import org.wildfly.clustering.server.group.Group;
import org.wildfly.clustering.server.infinispan.ClusteringServerLogger;
import org.wildfly.clustering.server.infinispan.group.InfinispanAddressResolver;
import org.wildfly.common.function.ExceptionRunnable;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Infinispan {@link Cache} based {@link ServiceProviderRegistry}.
 * This factory can create multiple {@link ServiceProviderRegistration} instance,
 * all of which share the same {@link Cache} instance.
 * @author Paul Ferraro
 * @param <T> the service identifier type
 */
@org.infinispan.notifications.Listener(observation = Observation.POST)
public class CacheServiceProviderRegistry<T> implements AutoCloseableServiceProviderRegistry<T> {

    private final Batcher<? extends Batch> batcher;
    private final ConcurrentMap<T, Map.Entry<Listener, ExecutorService>> listeners = new ConcurrentHashMap<>();
    private final Cache<T, Set<Address>> cache;
    private final Group<Address> group;
    private final Invoker invoker;
    private final Executor executor;

    public CacheServiceProviderRegistry(CacheServiceProviderRegistryConfiguration<T> config) {
        this.group = config.getGroup();
        this.cache = config.getCache();
        this.batcher = config.getBatcher();
        this.executor = config.getBlockingManager().asExecutor(this.getClass().getName());
        this.cache.addListener(this);
        this.invoker = new RetryingInvoker(this.cache);
    }

    @Override
    public void close() {
        this.cache.removeListener(this);
        // Cleanup any unclosed registrations
        for (Map.Entry<Listener, ExecutorService> entry : this.listeners.values()) {
            ExecutorService executor = entry.getValue();
            if (executor != null) {
                this.shutdown(executor);
            }
        }
        this.listeners.clear();
    }

    private void shutdown(ExecutorService executor) {
        WildFlySecurityManager.doUnchecked(executor, DefaultExecutorService.SHUTDOWN_ACTION);
        try {
            executor.awaitTermination(this.cache.getCacheConfiguration().transaction().cacheStopTimeout(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public org.wildfly.clustering.group.Group getGroup() {
        return this.group;
    }

    @Override
    public ServiceProviderRegistration<T> register(T service) {
        return this.register(service, null);
    }

    @Override
    public ServiceProviderRegistration<T> register(T service, Listener listener) {
        Map.Entry<Listener, ExecutorService> newEntry = new AbstractMap.SimpleEntry<>(listener, null);
        // Only create executor for new registrations
        Map.Entry<Listener, ExecutorService> entry = this.listeners.computeIfAbsent(service, key -> {
            if (listener != null) {
                newEntry.setValue(new DefaultExecutorService(listener.getClass(), ExecutorServiceFactory.SINGLE_THREAD));
            }
            return newEntry;
        });
        if (entry != newEntry) {
            throw new IllegalArgumentException(service.toString());
        }
        this.invoker.invoke(new RegisterLocalServiceTask(service));
        return new SimpleServiceProviderRegistration<>(service, this, () -> {
            Address localAddress = InfinispanAddressResolver.INSTANCE.apply(this.group.getLocalMember());
            try (Batch batch = this.batcher.createBatch()) {
                this.cache.getAdvancedCache().withFlags(Flag.FORCE_SYNCHRONOUS, Flag.IGNORE_RETURN_VALUES).compute(service, new AddressSetRemoveFunction(localAddress));
            } finally {
                Map.Entry<Listener, ExecutorService> oldEntry = this.listeners.remove(service);
                if (oldEntry != null) {
                    ExecutorService executor = oldEntry.getValue();
                    if (executor != null) {
                        this.shutdown(executor);
                    }
                }
            }
        });
    }

    void registerLocal(T service) {
        try (Batch batch = this.batcher.createBatch()) {
            this.register(InfinispanAddressResolver.INSTANCE.apply(this.group.getLocalMember()), service);
        }
    }

    void register(Address address, T service) {
        this.cache.getAdvancedCache().withFlags(Flag.FORCE_SYNCHRONOUS, Flag.IGNORE_RETURN_VALUES).compute(service, new AddressSetAddFunction(address));
    }

    @Override
    public Set<Node> getProviders(final T service) {
        Set<Address> addresses = this.cache.get(service);
        if (addresses == null) return Collections.emptySet();
        Set<Node> members = new TreeSet<>();
        for (Address address : addresses) {
            Node member = this.group.createNode(address);
            if (member != null) {
                members.add(member);
            }
        }
        return Collections.unmodifiableSet(members);
    }

    @Override
    public Set<T> getServices() {
        return this.cache.keySet();
    }

    @TopologyChanged
    public CompletionStage<Void> topologyChanged(TopologyChangedEvent<T, Set<Address>> event) {
        ConsistentHash previousHash = event.getWriteConsistentHashAtStart();
        List<Address> previousMembers = previousHash.getMembers();
        ConsistentHash hash = event.getWriteConsistentHashAtEnd();
        List<Address> members = hash.getMembers();

        if (!members.equals(previousMembers)) {
            Cache<T, Set<Address>> cache = event.getCache().getAdvancedCache().withFlags(Flag.FORCE_SYNCHRONOUS);
            Address localAddress = cache.getCacheManager().getAddress();

            // Determine which nodes have left the cache view
            Set<Address> leftMembers = new HashSet<>(previousMembers);
            leftMembers.removeAll(members);

            if (!leftMembers.isEmpty()) {
                Locality locality = new ConsistentHashLocality(cache, hash);
                // We're only interested in the entries for which we are the primary owner
                Iterator<Address> addresses = leftMembers.iterator();
                while (addresses.hasNext()) {
                    if (!locality.isLocal(addresses.next())) {
                        addresses.remove();
                    }
                }
            }

            // If this is a merge after cluster split: Re-assert services for local member
            Set<T> localServices = !previousMembers.contains(localAddress) ? this.listeners.keySet() : Collections.emptySet();

            if (!leftMembers.isEmpty() || !localServices.isEmpty()) {
                Batcher<? extends Batch> batcher = this.batcher;
                Invoker invoker = this.invoker;
                this.executor.execute(() -> {
                    if (!leftMembers.isEmpty()) {
                        try (Batch batch = batcher.createBatch()) {
                            try (CloseableIterator<T> keys = cache.getAdvancedCache().withFlags(Flag.FORCE_WRITE_LOCK).keySet().iterator()) {
                                while (keys.hasNext()) {
                                    cache.getAdvancedCache().withFlags(Flag.FORCE_SYNCHRONOUS, Flag.IGNORE_RETURN_VALUES).compute(keys.next(), new AddressSetRemoveFunction(leftMembers));
                                }
                            }
                        }
                    }
                    if (!localServices.isEmpty()) {
                        for (T localService : localServices) {
                            invoker.invoke(new RegisterLocalServiceTask(localService));
                        }
                    }
                });
            }
        }
        return CompletableFuture.completedStage(null);
    }

    @CacheEntryCreated
    @CacheEntryModified
    public CompletionStage<Void> modified(CacheEntryEvent<T, Set<Address>> event) {
        Map.Entry<Listener, ExecutorService> entry = this.listeners.get(event.getKey());
        if (entry != null) {
            Listener listener = entry.getKey();
            if (listener != null) {
                this.executor.execute(() -> {
                    try {
                        entry.getValue().submit(() -> {
                            Set<Node> members = new TreeSet<>();
                            for (Address address : event.getValue()) {
                                Node member = this.group.createNode(address);
                                if (member != null) {
                                    members.add(member);
                                }
                            }
                            try {
                                listener.providersChanged(members);
                            } catch (Throwable e) {
                                ClusteringServerLogger.ROOT_LOGGER.serviceProviderRegistrationListenerFailed(e, this.cache.getCacheManager().getCacheManagerConfiguration().cacheManagerName(), this.cache.getName(), members);
                            }
                        });
                    } catch (RejectedExecutionException e) {
                        // Executor was shutdown
                    }
                });
            }
        }
        return CompletableFuture.completedStage(null);
    }

    private class RegisterLocalServiceTask implements ExceptionRunnable<CacheException> {
        private final T localService;

        RegisterLocalServiceTask(T localService) {
            this.localService = localService;
        }

        @Override
        public void run() {
            CacheServiceProviderRegistry.this.registerLocal(this.localService);
        }
    }
}
