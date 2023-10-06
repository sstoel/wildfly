/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ejb3.component.stateful.cache.distributable;

import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import jakarta.ejb.ConcurrentAccessTimeoutException;

import org.jboss.as.ejb3.component.stateful.cache.StatefulSessionBean;
import org.jboss.as.ejb3.component.stateful.cache.StatefulSessionBeanCache;
import org.jboss.as.ejb3.component.stateful.cache.StatefulSessionBeanInstance;
import org.jboss.as.ejb3.component.stateful.cache.StatefulSessionBeanInstanceFactory;
import org.jboss.ejb.client.Affinity;
import org.wildfly.clustering.ee.Batch;
import org.wildfly.clustering.ee.Batcher;
import org.wildfly.clustering.ejb.bean.Bean;
import org.wildfly.clustering.ejb.bean.BeanManager;

/**
 * A distributable stateful session bean cache.
 * The availability of bean instances managed by this cache is determined by the underlying bean manager implementation.
 * @author Paul Ferraro
 * @param <K> the bean identifier type
 * @param <V> the bean instance type
 */
public class DistributableStatefulSessionBeanCache<K, V extends StatefulSessionBeanInstance<K>> implements StatefulSessionBeanCache<K, V> {
    private static final Object UNSET = Boolean.TRUE;

    private final BeanManager<K, V, Batch> manager;
    private final StatefulSessionBeanInstanceFactory<V> factory;

    public DistributableStatefulSessionBeanCache(DistributableStatefulSessionBeanCacheConfiguration<K, V> configuration) {
        this.manager = configuration.getBeanManager();
        this.factory = configuration.getInstanceFactory();
    }

    @Override
    public void start() {
        this.manager.start();
    }

    @Override
    public void stop() {
        this.manager.stop();
    }

    @Override
    public Affinity getStrongAffinity() {
        return this.manager.getStrongAffinity();
    }

    @Override
    public Affinity getWeakAffinity(K id) {
        return this.manager.getWeakAffinity(id);
    }

    @Override
    public K createStatefulSessionBean() {
        boolean newGroup = CURRENT_GROUP.get() == null;
        if (newGroup) {
            CURRENT_GROUP.set(UNSET);
        }
        Batcher<Batch> batcher = this.manager.getBatcher();
        try (Batch batch = batcher.createBatch()) {
            try {
                // This will invoke createStatefulBean() for nested beans
                // Nested beans will share the same group identifier
                V instance = this.factory.createInstance();
                K id = instance.getId();
                if (CURRENT_GROUP.get() == UNSET) {
                    CURRENT_GROUP.set(id);
                }
                try (@SuppressWarnings("unchecked") Bean<K, V> bean = this.manager.createBean(instance, (K) CURRENT_GROUP.get())) {
                    return bean.getId();
                }
            } catch (RuntimeException | Error e) {
                batch.discard();
                throw e;
            }
        } finally {
            if (newGroup) {
                CURRENT_GROUP.remove();
            }
        }
    }

    @Override
    public StatefulSessionBean<K, V> findStatefulSessionBean(K id) {
        Batcher<Batch> batcher = this.manager.getBatcher();
        // Batch is not closed here - it will be closed by the StatefulSessionBean
        @SuppressWarnings("resource")
        Batch batch = batcher.createBatch();
        try {
            // TODO WFLY-14167 Cache lookup timeout should reflect @AccessTimeout of associated bean/invocation
            Bean<K, V> bean = this.manager.findBean(id);
            if (bean == null) {
                batch.close();
                return null;
            }
            return new DistributableStatefulSessionBean<>(batcher, bean, batcher.suspendBatch());
        } catch (TimeoutException e) {
            batch.close();
            throw new ConcurrentAccessTimeoutException(e.getMessage());
        } catch (RuntimeException | Error e) {
            batch.discard();
            batch.close();
            throw e;
        }
    }

    @Override
    public int getActiveCount() {
        return this.manager.getActiveCount();
    }

    @Override
    public int getPassiveCount() {
        return this.manager.getPassiveCount();
    }

    @Override
    public Supplier<K> getIdentifierFactory() {
        return this.manager.getIdentifierFactory();
    }

    @Override
    public boolean isRemotable(Throwable throwable) {
        return this.manager.isRemotable(throwable);
    }
}
