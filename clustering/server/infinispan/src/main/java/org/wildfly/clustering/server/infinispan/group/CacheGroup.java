/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.server.infinispan.group;

import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.infinispan.Cache;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.configuration.global.TransportConfiguration;
import org.infinispan.distribution.DistributionManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.notifications.Listener.Observation;
import org.infinispan.notifications.cachelistener.annotation.TopologyChanged;
import org.infinispan.notifications.cachelistener.event.TopologyChangedEvent;
import org.infinispan.notifications.cachemanagerlistener.annotation.Merged;
import org.infinispan.notifications.cachemanagerlistener.annotation.ViewChanged;
import org.infinispan.notifications.cachemanagerlistener.event.ViewChangedEvent;
import org.infinispan.remoting.transport.Address;
import org.infinispan.remoting.transport.jgroups.JGroupsAddress;
import org.infinispan.util.concurrent.BlockingManager;
import org.wildfly.clustering.Registration;
import org.wildfly.clustering.context.DefaultExecutorService;
import org.wildfly.clustering.context.ExecutorServiceFactory;
import org.wildfly.clustering.group.GroupListener;
import org.wildfly.clustering.group.Membership;
import org.wildfly.clustering.group.Node;
import org.wildfly.clustering.server.NodeFactory;
import org.wildfly.clustering.server.group.Group;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * {@link Group} implementation based on the topology of a cache.
 * @author Paul Ferraro
 */
@org.infinispan.notifications.Listener(observation = Observation.POST)
public class CacheGroup implements AutoCloseableGroup<Address>, Function<GroupListener, ExecutorService> {

    private final Map<GroupListener, ExecutorService> listeners = new ConcurrentHashMap<>();
    private final Cache<?, ?> cache;
    private final NodeFactory<org.jgroups.Address> nodeFactory;
    private final SortedMap<Integer, Boolean> views = Collections.synchronizedSortedMap(new TreeMap<>());
    private final BlockingManager blocking;
    private final Executor executor;

    public CacheGroup(CacheGroupConfiguration config) {
        this.cache = config.getCache();
        this.nodeFactory = config.getMemberFactory();
        this.blocking = config.getBlockingManager();
        this.executor = this.blocking.asExecutor(this.getClass().getName());
        this.cache.getCacheManager().addListener(this);
        this.cache.addListener(this);
    }

    @Override
    public void close() {
        this.cache.removeListener(this);
        this.cache.getCacheManager().removeListener(this);
        // Cleanup any unregistered listeners
        for (ExecutorService executor : this.listeners.values()) {
            this.shutdown(executor);
        }
        this.listeners.clear();
    }

    private void shutdown(ExecutorService executor) {
        WildFlySecurityManager.doUnchecked(executor, DefaultExecutorService.SHUTDOWN_NOW_ACTION);
        try {
            executor.awaitTermination(this.cache.getCacheConfiguration().transaction().cacheStopTimeout(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String getName() {
        GlobalConfiguration global = this.cache.getCacheManager().getCacheManagerConfiguration();
        TransportConfiguration transport = global.transport();
        return transport.transport() != null ? transport.clusterName() : global.cacheManagerName();
    }

    @Override
    public Node getLocalMember() {
        return this.createNode(this.cache.getCacheManager().getAddress());
    }

    @Override
    public Membership getMembership() {
        EmbeddedCacheManager manager = this.cache.getCacheManager();
        DistributionManager dist = this.cache.getAdvancedCache().getDistributionManager();
        return (dist != null) ? new CacheMembership(manager.getAddress(), dist.getCacheTopology(), this) : new CacheMembership(manager, this);
    }

    @Override
    public boolean isSingleton() {
        return false;
    }

    @Override
    public Node createNode(Address address) {
        if (!(address instanceof JGroupsAddress)) {
            throw new IllegalArgumentException(address.toString());
        }
        return this.nodeFactory.createNode(((JGroupsAddress) address).getJGroupsAddress());
    }

    @Merged
    @ViewChanged
    public CompletionStage<Void> viewChanged(ViewChangedEvent event) {
        if (this.cache.getAdvancedCache().getDistributionManager() != null) {
            // Record view status for use by @TopologyChanged event
            return this.blocking.runBlocking(() -> this.views.put(event.getViewId(), event.isMergeView()), event.getViewId());
        }

        Membership previousMembership = new CacheMembership(event.getLocalAddress(), event.getOldMembers(), this);
        Membership membership = new CacheMembership(event.getLocalAddress(), event.getNewMembers(), this);
        if (!this.listeners.isEmpty()) {
            this.executor.execute(new GroupListenerNotificationTask(this.listeners.entrySet(), previousMembership, membership, event.isMergeView()));
        }
        return CompletableFuture.completedStage(null);
    }

    @TopologyChanged
    public CompletionStage<Void> topologyChanged(TopologyChangedEvent<?, ?> event) {
        int viewId = event.getCache().getAdvancedCache().getRpcManager().getTransport().getViewId();
        Address localAddress = event.getCache().getCacheManager().getAddress();
        Membership previousMembership = new CacheMembership(localAddress, event.getWriteConsistentHashAtStart(), this);
        Membership membership = new CacheMembership(localAddress, event.getWriteConsistentHashAtEnd(), this);

        this.executor.execute(() -> {
            Boolean status = this.views.get(viewId);
            boolean merged = (status != null) ? status : false;
            new GroupListenerNotificationTask(this.listeners.entrySet(), previousMembership, membership, merged).run();
            // Purge obsolete views
            this.views.headMap(viewId).clear();
        });
        return CompletableFuture.completedStage(null);
    }

    @Override
    public Registration register(GroupListener listener) {
        this.listeners.computeIfAbsent(listener, this);
        return () -> this.unregister(listener);
    }

    @Override
    public ExecutorService apply(GroupListener listener) {
        return new DefaultExecutorService(listener.getClass(), ExecutorServiceFactory.SINGLE_THREAD);
    }

    private void unregister(GroupListener listener) {
        ExecutorService executor = this.listeners.remove(listener);
        if (executor != null) {
            this.shutdown(executor);
        }
    }
}
