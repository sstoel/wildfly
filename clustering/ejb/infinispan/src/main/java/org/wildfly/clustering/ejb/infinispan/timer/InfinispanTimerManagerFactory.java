/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.ejb.infinispan.timer;

import java.util.function.Supplier;

import org.infinispan.Cache;
import org.infinispan.remoting.transport.Address;
import org.wildfly.clustering.ee.Batcher;
import org.wildfly.clustering.ee.cache.CacheProperties;
import org.wildfly.clustering.ee.cache.tx.TransactionBatch;
import org.wildfly.clustering.ejb.cache.timer.RemappableTimerMetaDataEntry;
import org.wildfly.clustering.ejb.cache.timer.TimerFactory;
import org.wildfly.clustering.ejb.cache.timer.TimerMetaDataFactory;
import org.wildfly.clustering.ejb.timer.TimerManager;
import org.wildfly.clustering.ejb.timer.TimerManagerConfiguration;
import org.wildfly.clustering.ejb.timer.TimerManagerFactory;
import org.wildfly.clustering.ejb.timer.TimerRegistry;
import org.wildfly.clustering.infinispan.affinity.KeyAffinityServiceFactory;
import org.wildfly.clustering.marshalling.spi.ByteBufferMarshalledValueFactory;
import org.wildfly.clustering.marshalling.spi.ByteBufferMarshaller;
import org.wildfly.clustering.marshalling.spi.MarshalledValue;
import org.wildfly.clustering.marshalling.spi.MarshalledValueMarshaller;
import org.wildfly.clustering.marshalling.spi.Marshaller;
import org.wildfly.clustering.server.dispatcher.CommandDispatcherFactory;
import org.wildfly.clustering.server.group.Group;

/**
 * @author Paul Ferraro
 */
public class InfinispanTimerManagerFactory<I> implements TimerManagerFactory<I, TransactionBatch> {

    private final InfinispanTimerManagerFactoryConfiguration<I> configuration;

    public InfinispanTimerManagerFactory(InfinispanTimerManagerFactoryConfiguration<I> configuration) {
        this.configuration = configuration;
    }

    @Override
    public TimerManager<I, TransactionBatch> createTimerManager(TimerManagerConfiguration<I, TransactionBatch> configuration) {
        InfinispanTimerManagerFactoryConfiguration<I> factoryConfiguration = this.configuration;
        Marshaller<Object, MarshalledValue<Object, ByteBufferMarshaller>> marshaller = new MarshalledValueMarshaller<>(new ByteBufferMarshalledValueFactory(this.configuration.getMarshaller()));

        InfinispanTimerMetaDataConfiguration<MarshalledValue<Object, ByteBufferMarshaller>> metaDataFactoryConfig = new InfinispanTimerMetaDataConfiguration<>() {
            @Override
            public Marshaller<Object, MarshalledValue<Object, ByteBufferMarshaller>> getMarshaller() {
                return marshaller;
            }

            @Override
            public boolean isPersistent() {
                return configuration.isPersistent();
            }

            @Override
            public <K, V> Cache<K, V> getCache() {
                return factoryConfiguration.getCache();
            }
        };
        TimerMetaDataFactory<I, RemappableTimerMetaDataEntry<MarshalledValue<Object, ByteBufferMarshaller>>, MarshalledValue<Object, ByteBufferMarshaller>> metaDataFactory = new InfinispanTimerMetaDataFactory<>(metaDataFactoryConfig);
        TimerFactory<I, RemappableTimerMetaDataEntry<MarshalledValue<Object, ByteBufferMarshaller>>, MarshalledValue<Object, ByteBufferMarshaller>> factory = new InfinispanTimerFactory<>(metaDataFactory, configuration.getListener(), this.configuration.getRegistry());

        return new InfinispanTimerManager<>(new InfinispanTimerManagerConfiguration<I, MarshalledValue<Object, ByteBufferMarshaller>>() {
            @Override
            public <K, V> Cache<K, V> getCache() {
                return factoryConfiguration.getCache();
            }

            @Override
            public CacheProperties getCacheProperties() {
                return factoryConfiguration.getCacheProperties();
            }

            @Override
            public TimerFactory<I, RemappableTimerMetaDataEntry<MarshalledValue<Object, ByteBufferMarshaller>>, MarshalledValue<Object, ByteBufferMarshaller>> getTimerFactory() {
                return factory;
            }

            @Override
            public TimerRegistry<I> getRegistry() {
                return factoryConfiguration.getRegistry();
            }

            @Override
            public Marshaller<Object, MarshalledValue<Object, ByteBufferMarshaller>> getMarshaller() {
                return marshaller;
            }

            @Override
            public Batcher<TransactionBatch> getBatcher() {
                return factoryConfiguration.getBatcher();
            }

            @Override
            public Supplier<I> getIdentifierFactory() {
                return factoryConfiguration.getIdentifierFactory();
            }

            @Override
            public KeyAffinityServiceFactory getKeyAffinityServiceFactory() {
                return factoryConfiguration.getKeyAffinityServiceFactory();
            }

            @Override
            public CommandDispatcherFactory getCommandDispatcherFactory() {
                return factoryConfiguration.getCommandDispatcherFactory();
            }

            @Override
            public Group<Address> getGroup() {
                return factoryConfiguration.getGroup();
            }
        });
    }
}
