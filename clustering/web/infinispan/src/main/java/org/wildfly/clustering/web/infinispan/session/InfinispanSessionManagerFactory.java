/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.web.infinispan.session;

import java.time.Duration;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.infinispan.Cache;
import org.wildfly.clustering.Registrar;
import org.wildfly.clustering.Registration;
import org.wildfly.clustering.dispatcher.CommandDispatcherFactory;
import org.wildfly.clustering.ee.Key;
import org.wildfly.clustering.ee.cache.CacheProperties;
import org.wildfly.clustering.ee.cache.ConcurrentManager;
import org.wildfly.clustering.ee.cache.IdentifierFactory;
import org.wildfly.clustering.ee.cache.SimpleManager;
import org.wildfly.clustering.ee.cache.tx.TransactionBatch;
import org.wildfly.clustering.ee.expiration.ExpirationMetaData;
import org.wildfly.clustering.ee.infinispan.InfinispanConfiguration;
import org.wildfly.clustering.ee.infinispan.PrimaryOwnerLocator;
import org.wildfly.clustering.ee.infinispan.affinity.AffinityIdentifierFactory;
import org.wildfly.clustering.ee.infinispan.expiration.ScheduleWithExpirationMetaDataCommandFactory;
import org.wildfly.clustering.ee.infinispan.scheduler.CacheEntryScheduler;
import org.wildfly.clustering.ee.infinispan.scheduler.PrimaryOwnerScheduler;
import org.wildfly.clustering.ee.infinispan.scheduler.ScheduleLocalKeysTask;
import org.wildfly.clustering.ee.infinispan.scheduler.ScheduleWithTransientMetaDataCommand;
import org.wildfly.clustering.ee.infinispan.scheduler.SchedulerTopologyChangeListener;
import org.wildfly.clustering.group.Group;
import org.wildfly.clustering.infinispan.affinity.KeyAffinityServiceFactory;
import org.wildfly.clustering.infinispan.distribution.CacheLocality;
import org.wildfly.clustering.infinispan.distribution.Locality;
import org.wildfly.clustering.infinispan.distribution.SimpleLocality;
import org.wildfly.clustering.infinispan.listener.ListenerRegistration;
import org.wildfly.clustering.marshalling.spi.ByteBufferMarshaller;
import org.wildfly.clustering.marshalling.spi.MarshalledValue;
import org.wildfly.clustering.web.cache.session.CompositeSessionFactory;
import org.wildfly.clustering.web.cache.session.ConcurrentSessionManager;
import org.wildfly.clustering.web.cache.session.DelegatingSessionManagerConfiguration;
import org.wildfly.clustering.web.cache.session.SessionFactory;
import org.wildfly.clustering.web.cache.session.attributes.MarshalledValueSessionAttributesFactoryConfiguration;
import org.wildfly.clustering.web.cache.session.attributes.SessionAttributesFactory;
import org.wildfly.clustering.web.cache.session.attributes.fine.SessionAttributeActivationNotifier;
import org.wildfly.clustering.web.cache.session.metadata.SessionMetaDataFactory;
import org.wildfly.clustering.web.cache.session.metadata.coarse.ContextualSessionMetaDataEntry;
import org.wildfly.clustering.web.infinispan.session.attributes.CoarseSessionAttributesFactory;
import org.wildfly.clustering.web.infinispan.session.attributes.FineSessionAttributesFactory;
import org.wildfly.clustering.web.infinispan.session.attributes.InfinispanSessionAttributesFactoryConfiguration;
import org.wildfly.clustering.web.infinispan.session.metadata.InfinispanSessionMetaDataFactory;
import org.wildfly.clustering.web.infinispan.session.metadata.SessionMetaDataKey;
import org.wildfly.clustering.web.infinispan.session.metadata.SessionMetaDataKeyFilter;
import org.wildfly.clustering.web.session.SessionManager;
import org.wildfly.clustering.web.session.SessionManagerConfiguration;
import org.wildfly.clustering.web.session.SessionManagerFactory;
import org.wildfly.clustering.web.session.SpecificationProvider;

/**
 * Factory for creating session managers.
 * @param <S> the HttpSession specification type
 * @param <SC> the ServletContext specification type
 * @param <AL> the HttpSessionAttributeListener specification type
 * @param <LC> the local context type
 * @author Paul Ferraro
 */
public class InfinispanSessionManagerFactory<S, SC, AL, LC> implements SessionManagerFactory<SC, LC, TransactionBatch>, Runnable {

    private final org.wildfly.clustering.ee.Scheduler<String, ExpirationMetaData> scheduler;
    private final SpecificationProvider<S, SC, AL> provider;
    private final KeyAffinityServiceFactory affinityFactory;
    private final SessionFactory<SC, ContextualSessionMetaDataEntry<LC>, ?, LC> factory;
    private final BiConsumer<Locality, Locality> scheduleTask;
    private final ListenerRegistration schedulerListenerRegistration;
    private final InfinispanConfiguration configuration;
    private final ExpiredSessionRemover<SC, ?, ?, LC> remover;
    private final SessionAttributeActivationNotifierFactory<S, SC, AL, LC, TransactionBatch> notifierFactory;

    public InfinispanSessionManagerFactory(InfinispanSessionManagerFactoryConfiguration<S, SC, AL, LC> config) {
        this.configuration = config;
        this.affinityFactory = config.getKeyAffinityServiceFactory();
        this.provider = config.getSpecificationProvider();
        this.notifierFactory = new SessionAttributeActivationNotifierFactory<>(this.provider);
        CacheProperties properties = config.getCacheProperties();
        SessionMetaDataFactory<ContextualSessionMetaDataEntry<LC>> metaDataFactory = new InfinispanSessionMetaDataFactory<>(config);
        this.factory = new CompositeSessionFactory<>(metaDataFactory, this.createSessionAttributesFactory(config), config.getLocalContextFactory());
        this.remover = new ExpiredSessionRemover<>(this.factory);
        Cache<Key<String>, ?> cache = config.getCache();
        CacheEntryScheduler<String, ExpirationMetaData> localScheduler = new SessionExpirationScheduler<>(config.getBatcher(), this.factory.getMetaDataFactory(), this.remover, Duration.ofMillis(cache.getCacheConfiguration().transaction().cacheStopTimeout()));
        CommandDispatcherFactory dispatcherFactory = config.getCommandDispatcherFactory();
        Group group = dispatcherFactory.getGroup();
        this.scheduler = group.isSingleton() ? localScheduler : new PrimaryOwnerScheduler<>(dispatcherFactory, cache.getName(), localScheduler, new PrimaryOwnerLocator<>(cache, config.getMemberFactory()), SessionMetaDataKey::new, properties.isTransactional() ? new ScheduleWithExpirationMetaDataCommandFactory<>() : ScheduleWithTransientMetaDataCommand::new);

        this.scheduleTask = new ScheduleLocalKeysTask<>(cache, SessionMetaDataKeyFilter.INSTANCE, localScheduler);
        this.schedulerListenerRegistration = new SchedulerTopologyChangeListener<>(cache, localScheduler, this.scheduleTask).register();
    }

    @Override
    public void run() {
        this.scheduleTask.accept(new SimpleLocality(false), new CacheLocality(this.configuration.getCache()));
    }

    @Override
    public SessionManager<LC, TransactionBatch> createSessionManager(final SessionManagerConfiguration<SC> configuration) {
        IdentifierFactory<String> identifierFactory = new AffinityIdentifierFactory<>(configuration.getIdentifierFactory(), this.configuration.getCache(), this.affinityFactory);
        Registrar<SessionManager<LC, TransactionBatch>> registrar = manager -> {
            Registration contextRegistration = this.notifierFactory.register(Map.entry(configuration.getServletContext(), manager));
            Registration expirationRegistration = this.remover.register(configuration.getExpirationListener());
            return () -> {
                expirationRegistration.close();
                contextRegistration.close();
            };
        };
        org.wildfly.clustering.ee.Scheduler<String, ExpirationMetaData> scheduler = this.scheduler;
        InfinispanSessionManagerConfiguration<SC, LC> config = new AbstractInfinispanSessionManagerConfiguration<>(configuration, identifierFactory, this.configuration) {
            @Override
            public org.wildfly.clustering.ee.Scheduler<String, ExpirationMetaData> getExpirationScheduler() {
                return scheduler;
            }

            @Override
            public Runnable getStartTask() {
                return InfinispanSessionManagerFactory.this;
            }

            @Override
            public Registrar<SessionManager<LC, TransactionBatch>> getRegistrar() {
                return registrar;
            }
        };
        return new ConcurrentSessionManager<>(new InfinispanSessionManager<>(this.factory, config), this.configuration.getCacheProperties().isTransactional() ? SimpleManager::new : ConcurrentManager::new);
    }

    private SessionAttributesFactory<SC, ?> createSessionAttributesFactory(InfinispanSessionManagerFactoryConfiguration<S, SC, AL, LC> configuration) {
        switch (configuration.getAttributePersistenceStrategy()) {
            case FINE: {
                return new FineSessionAttributesFactory<>(new InfinispanMarshalledValueSessionAttributesFactoryConfiguration<>(configuration, this.notifierFactory));
            }
            case COARSE: {
                return new CoarseSessionAttributesFactory<>(new InfinispanMarshalledValueSessionAttributesFactoryConfiguration<>(configuration, this.notifierFactory));
            }
            default: {
                // Impossible
                throw new IllegalStateException();
            }
        }
    }

    @Override
    public void close() {
        this.schedulerListenerRegistration.close();
        this.scheduler.close();
        this.factory.close();
    }

    private abstract static class AbstractInfinispanSessionManagerConfiguration<SC, LC> extends DelegatingSessionManagerConfiguration<SC> implements InfinispanSessionManagerConfiguration<SC, LC> {
        private final InfinispanConfiguration configuration;
        private final IdentifierFactory<String> identifierFactory;

        AbstractInfinispanSessionManagerConfiguration(SessionManagerConfiguration<SC> managerConfiguration, IdentifierFactory<String> identifierFactory, InfinispanConfiguration configuration) {
            super(managerConfiguration);
            this.identifierFactory = identifierFactory;
            this.configuration = configuration;
        }

        @Override
        public IdentifierFactory<String> getIdentifierFactory() {
            return this.identifierFactory;
        }

        @Override
        public <K, V> Cache<K, V> getCache() {
            return this.configuration.getCache();
        }
    }

    private static class InfinispanMarshalledValueSessionAttributesFactoryConfiguration<S, SC, AL, V, LC> extends MarshalledValueSessionAttributesFactoryConfiguration<S, SC, AL, V, LC> implements InfinispanSessionAttributesFactoryConfiguration<S, SC, AL, V, MarshalledValue<V, ByteBufferMarshaller>> {
        private final InfinispanSessionManagerFactoryConfiguration<S, SC, AL, LC> configuration;
        private final Function<String, SessionAttributeActivationNotifier> notifierFactory;

        InfinispanMarshalledValueSessionAttributesFactoryConfiguration(InfinispanSessionManagerFactoryConfiguration<S, SC, AL, LC> configuration, Function<String, SessionAttributeActivationNotifier> notifierFactory) {
            super(configuration);
            this.configuration = configuration;
            this.notifierFactory = notifierFactory;
        }

        @Override
        public <CK, CV> Cache<CK, CV> getCache() {
            return this.configuration.getCache();
        }

        @Override
        public Function<String, SessionAttributeActivationNotifier> getActivationNotifierFactory() {
            return this.notifierFactory;
        }
    }
}
