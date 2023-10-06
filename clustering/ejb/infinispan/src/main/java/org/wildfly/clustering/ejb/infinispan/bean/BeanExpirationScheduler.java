/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.ejb.infinispan.bean;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.wildfly.clustering.ee.Batch;
import org.wildfly.clustering.ee.Batcher;
import org.wildfly.clustering.ee.cache.scheduler.LinkedScheduledEntries;
import org.wildfly.clustering.ee.cache.scheduler.LocalScheduler;
import org.wildfly.clustering.ee.cache.scheduler.SortedScheduledEntries;
import org.wildfly.clustering.ee.cache.tx.TransactionBatch;
import org.wildfly.clustering.ee.infinispan.expiration.AbstractExpirationScheduler;
import org.wildfly.clustering.ejb.bean.Bean;
import org.wildfly.clustering.ejb.bean.BeanExpirationConfiguration;
import org.wildfly.clustering.ejb.bean.BeanInstance;
import org.wildfly.clustering.ejb.bean.ImmutableBeanMetaData;
import org.wildfly.clustering.ejb.cache.bean.BeanFactory;
import org.wildfly.clustering.ejb.cache.bean.ImmutableBeanMetaDataFactory;
import org.wildfly.clustering.ejb.infinispan.logging.InfinispanEjbLogger;
import org.wildfly.clustering.group.Group;

/**
 * Schedules a bean for expiration.
 * @author Paul Ferraro
 * @param <K> the bean identifier type
 * @param <V> the bean instance type
 * @param <M> the metadata value type
 */
public class BeanExpirationScheduler<K, V extends BeanInstance<K>, M> extends AbstractExpirationScheduler<K> {

    private final ImmutableBeanMetaDataFactory<K, M> factory;

    public BeanExpirationScheduler(Group group, Batcher<TransactionBatch> batcher, BeanFactory<K, V, M> factory, BeanExpirationConfiguration<K, V> expiration, Duration closeTimeout) {
        super(new LocalScheduler<>(group.isSingleton() ? new LinkedScheduledEntries<>() : new SortedScheduledEntries<>(), new BeanRemoveTask<>(batcher, factory, expiration.getExpirationListener()), closeTimeout));
        this.factory = factory.getMetaDataFactory();
    }

    @Override
    public void schedule(K id) {
        M value = this.factory.findValue(id);
        if (value != null) {
            ImmutableBeanMetaData<K> metaData = this.factory.createImmutableBeanMetaData(id, value);
            this.schedule(id, metaData);
        }
    }

    private static class BeanRemoveTask<K, V extends BeanInstance<K>, M> implements Predicate<K> {
        private final Batcher<TransactionBatch> batcher;
        private final BeanFactory<K, V, M> factory;
        private final Consumer<V> timeoutListener;

        BeanRemoveTask(Batcher<TransactionBatch> batcher, BeanFactory<K, V, M> factory, Consumer<V> timeoutListener) {
            this.batcher = batcher;
            this.timeoutListener = timeoutListener;
            this.factory = factory;
        }

        @Override
        public boolean test(K id) {
            InfinispanEjbLogger.ROOT_LOGGER.tracef("Expiring stateful session bean %s", id);
            try (Batch batch = this.batcher.createBatch()) {
                try {
                    M value = this.factory.tryValue(id);
                    if (value != null) {
                        try (Bean<K, V> bean = this.factory.createBean(id, value)) {
                            // Ensure bean is actually expired
                            if (bean.getMetaData().isExpired()) {
                                bean.remove(this.timeoutListener);
                            }
                        }
                    }
                    return true;
                } catch (RuntimeException e) {
                    batch.discard();
                    throw e;
                }
            } catch (RuntimeException e) {
                InfinispanEjbLogger.ROOT_LOGGER.failedToExpireBean(e, id);
                return false;
            }
        }
    }
}
