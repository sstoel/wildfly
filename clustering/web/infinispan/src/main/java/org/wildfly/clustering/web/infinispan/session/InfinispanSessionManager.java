/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.web.infinispan.session;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.PersistenceConfiguration;
import org.infinispan.configuration.cache.StoreConfiguration;
import org.infinispan.context.Flag;
import org.wildfly.clustering.Registrar;
import org.wildfly.clustering.Registration;
import org.wildfly.clustering.ee.Batcher;
import org.wildfly.clustering.ee.Key;
import org.wildfly.clustering.ee.Scheduler;
import org.wildfly.clustering.ee.cache.CacheProperties;
import org.wildfly.clustering.ee.cache.IdentifierFactory;
import org.wildfly.clustering.ee.cache.tx.TransactionBatch;
import org.wildfly.clustering.ee.expiration.Expiration;
import org.wildfly.clustering.ee.expiration.ExpirationMetaData;
import org.wildfly.clustering.infinispan.distribution.CacheLocality;
import org.wildfly.clustering.infinispan.distribution.Locality;
import org.wildfly.clustering.web.cache.session.SessionFactory;
import org.wildfly.clustering.web.cache.session.SimpleImmutableSession;
import org.wildfly.clustering.web.cache.session.ValidSession;
import org.wildfly.clustering.web.infinispan.logging.InfinispanWebLogger;
import org.wildfly.clustering.web.infinispan.session.metadata.SessionMetaDataKeyFilter;
import org.wildfly.clustering.web.session.ImmutableSession;
import org.wildfly.clustering.web.session.Session;
import org.wildfly.clustering.web.session.SessionManager;

/**
 * Generic session manager implementation - independent of cache mapping strategy.
 * @param <SC> the ServletContext specification type
 * @param <MV> the meta-data value type
 * @param <AV> the attributes value type
 * @param <LC> the local context type
 * @author Paul Ferraro
 */
public class InfinispanSessionManager<SC, MV, AV, LC> implements SessionManager<LC, TransactionBatch> {

    private final Consumer<ImmutableSession> expirationListener;
    private final Batcher<TransactionBatch> batcher;
    private final Cache<Key<String>, ?> cache;
    private final CacheProperties properties;
    private final SessionFactory<SC, MV, AV, LC> factory;
    private final IdentifierFactory<String> identifierFactory;
    private final Scheduler<String, ExpirationMetaData> expirationScheduler;
    private final SC context;
    private final Runnable startTask;
    private final Consumer<ImmutableSession> closeTask;
    private final Registrar<SessionManager<LC, TransactionBatch>> registrar;
    private final Expiration expiration;

    private volatile Registration registration;

    public InfinispanSessionManager(SessionFactory<SC, MV, AV, LC> factory, InfinispanSessionManagerConfiguration<SC, LC> configuration) {
        this.factory = factory;
        this.cache = configuration.getCache();
        this.properties = configuration.getCacheProperties();
        this.expirationListener = configuration.getExpirationListener();
        this.identifierFactory = configuration.getIdentifierFactory();
        this.batcher = configuration.getBatcher();
        this.expirationScheduler = configuration.getExpirationScheduler();
        this.context = configuration.getServletContext();
        this.registrar = configuration.getRegistrar();
        this.startTask = configuration.getStartTask();
        this.expiration = configuration;
        this.closeTask = new Consumer<>() {
            @Override
            public void accept(ImmutableSession session) {
                if (session.isValid()) {
                    configuration.getExpirationScheduler().schedule(session.getId(), session.getMetaData());
                }
            }
        };
    }

    @Override
    public void start() {
        this.registration = this.registrar.register(this);
        this.identifierFactory.start();
        this.startTask.run();
    }

    @Override
    public void stop() {
        if (!this.properties.isPersistent()) {
            PersistenceConfiguration persistence = this.cache.getCacheConfiguration().persistence();
            // Don't passivate sessions on stop if we will purge the store on startup
            if (persistence.passivation() && !persistence.stores().stream().allMatch(StoreConfiguration::purgeOnStartup)) {
                try (Stream<Key<String>> stream = this.cache.getAdvancedCache().withFlags(Flag.CACHE_MODE_LOCAL, Flag.SKIP_CACHE_LOAD, Flag.SKIP_LOCKING).keySet().stream()) {
                    stream.filter(SessionMetaDataKeyFilter.INSTANCE).forEach(this.cache::evict);
                }
            }
        }
        this.identifierFactory.stop();
        this.registration.close();
    }

    @Override
    public Duration getStopTimeout() {
        return Duration.ofMillis(this.cache.getCacheConfiguration().transaction().cacheStopTimeout());
    }

    @Override
    public Batcher<TransactionBatch> getBatcher() {
        return this.batcher;
    }

    @Override
    public Supplier<String> getIdentifierFactory() {
        return this.identifierFactory;
    }

    @Override
    public Session<LC> findSession(String id) {
        Map.Entry<MV, AV> value = this.factory.findValue(id);
        if (value == null) {
            InfinispanWebLogger.ROOT_LOGGER.tracef("Session %s not found", id);
            return null;
        }
        ImmutableSession session = this.factory.createImmutableSession(id, value);
        if (session.getMetaData().isExpired()) {
            InfinispanWebLogger.ROOT_LOGGER.tracef("Session %s was found, but has expired", id);
            this.expirationListener.accept(session);
            this.factory.remove(id);
            return null;
        }
        this.expirationScheduler.cancel(id);

        return new ValidSession<>(this.factory.createSession(id, value, this.context), this.closeTask);
    }

    @Override
    public Session<LC> createSession(String id) {
        Map.Entry<MV, AV> entry = this.factory.createValue(id, this.expiration.getTimeout());
        if (entry == null) return null;
        Session<LC> session = this.factory.createSession(id, entry, this.context);
        return new ValidSession<>(session, this.closeTask);
    }

    @Override
    public ImmutableSession readSession(String id) {
        Map.Entry<MV, AV> value = this.factory.findValue(id);
        return (value != null) ? new SimpleImmutableSession(this.factory.createImmutableSession(id, value)) : null;
    }

    @Override
    public Set<String> getActiveSessions() {
        // Omit remote sessions (i.e. when using DIST mode) as well as passivated sessions
        return this.getSessions(Flag.CACHE_MODE_LOCAL, Flag.SKIP_CACHE_LOAD);
    }

    @Override
    public Set<String> getLocalSessions() {
        // Omit remote sessions (i.e. when using DIST mode)
        return this.getSessions(Flag.CACHE_MODE_LOCAL);
    }

    private Set<String> getSessions(Flag... flags) {
        Locality locality = new CacheLocality(this.cache);
        try (Stream<Key<String>> keys = this.cache.getAdvancedCache().withFlags(flags).keySet().stream()) {
            return keys.filter(SessionMetaDataKeyFilter.INSTANCE.and(key -> locality.isLocal(key))).map(key -> key.getId()).collect(Collectors.toSet());
        }
    }

    @Override
    public long getActiveSessionCount() {
        return this.getActiveSessions().size();
    }
}
