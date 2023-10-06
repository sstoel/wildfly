/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.clustering;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.ejb.Remote;
import jakarta.ejb.Stateless;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.infinispan.Cache;
import org.infinispan.distribution.DistributionManager;
import org.infinispan.distribution.LocalizedCacheTopology;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.Listener.Observation;
import org.infinispan.notifications.cachelistener.annotation.TopologyChanged;
import org.infinispan.notifications.cachelistener.event.TopologyChangedEvent;
import org.infinispan.util.concurrent.BlockingManager;
import org.jboss.logging.Logger;

/**
 * Jakarta Enterprise Beans that establishes a stable topology.
 * @author Paul Ferraro
 */
@Stateless
@Remote(TopologyChangeListener.class)
@Listener(observation = Observation.POST)
public class TopologyChangeListenerBean implements TopologyChangeListener, Runnable {

    private static final Logger logger = Logger.getLogger(TopologyChangeListenerBean.class);

    @Override
    public void establishTopology(String containerName, String cacheName, long timeout, String... nodes) throws InterruptedException {
        Set<String> expectedMembers = Stream.of(nodes).collect(Collectors.toSet());
        Cache<?, ?> cache = findCache(containerName, cacheName);
        if (cache == null) {
            throw new IllegalStateException(String.format("Cache %s.%s not found", containerName, cacheName));
        }
        cache.addListener(this);
        try {
            synchronized (this) {
                DistributionManager dist = cache.getAdvancedCache().getDistributionManager();
                LocalizedCacheTopology topology = dist.getCacheTopology();
                Set<String> members = getMembers(topology);
                long start = System.currentTimeMillis();
                long now = start;
                long endTime = start + timeout;
                while (!expectedMembers.equals(members)) {
                    logger.infof("%s != %s, waiting for a topology change event. Current topology id = %d", expectedMembers, members, topology.getTopologyId());
                    this.wait(endTime - now);
                    now = System.currentTimeMillis();
                    if (now >= endTime) {
                        throw new InterruptedException(String.format("Cache %s/%s failed to establish view %s within %d ms.  Current view is: %s", containerName, cacheName, expectedMembers, timeout, members));
                    }
                    topology = dist.getCacheTopology();
                    members = getMembers(topology);
                }
                logger.infof("Cache %s/%s successfully established view %s within %d ms. Topology id = %d", containerName, cacheName, expectedMembers, now - start, topology.getTopologyId());
            }
        } finally {
            cache.removeListener(this);
        }
    }

    private static Cache<?, ?> findCache(String containerName, String cacheName) {
        try {
            Context context = new InitialContext();
            try {
                EmbeddedCacheManager manager = (EmbeddedCacheManager) context.lookup("java:jboss/infinispan/container/" + containerName);
                return manager.cacheExists(cacheName) ? manager.getCache(cacheName) : null;
            } finally {
                context.close();
            }
        } catch (NamingException e) {
            return null;
        }
    }

    private static Set<String> getMembers(LocalizedCacheTopology topology) {
        return topology.getMembers().stream().map(Object::toString).sorted().collect(Collectors.toSet());
    }

    @TopologyChanged
    public CompletionStage<Void> topologyChanged(TopologyChangedEvent<?, ?> event) {
        @SuppressWarnings("deprecation")
        BlockingManager blocking = event.getCache().getCacheManager().getGlobalComponentRegistry().getComponent(BlockingManager.class);
        blocking.asExecutor(this.getClass().getName()).execute(this);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void run() {
        synchronized (this) {
            this.notify();
        }
    }
}
