/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.web.undertow.session;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import io.undertow.UndertowMessages;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.session.SessionConfig;
import io.undertow.server.session.SessionListener;
import io.undertow.server.session.SessionListeners;
import io.undertow.server.session.SessionManagerStatistics;
import io.undertow.util.AttachmentKey;

import org.wildfly.clustering.ee.Batch;
import org.wildfly.clustering.ee.Batcher;
import org.wildfly.clustering.web.IdentifierMarshaller;
import org.wildfly.clustering.web.session.Session;
import org.wildfly.clustering.web.session.SessionManager;
import org.wildfly.clustering.web.session.oob.OOBSession;
import org.wildfly.clustering.web.undertow.UndertowIdentifierSerializerProvider;
import org.wildfly.clustering.web.undertow.logging.UndertowClusteringLogger;
import org.wildfly.common.function.Functions;

/**
 * Adapts a distributable {@link SessionManager} to an Undertow {@link io.undertow.server.session.SessionManager}.
 * @author Paul Ferraro
 */
public class DistributableSessionManager implements UndertowSessionManager, LongConsumer {

    private static final IdentifierMarshaller IDENTIFIER_MARSHALLER = new UndertowIdentifierSerializerProvider().getMarshaller();

    private final AttachmentKey<io.undertow.server.session.Session> key = AttachmentKey.create(io.undertow.server.session.Session.class);
    private final String deploymentName;
    private final SessionListeners listeners;
    private final SessionManager<Map<String, Object>, Batch> manager;
    private final RecordableSessionManagerStatistics statistics;
    private final StampedLock lifecycleLock = new StampedLock();

    // Matches io.undertow.server.session.InMemorySessionManager
    private volatile int defaultSessionTimeout = 30 * 60;

    // Guarded by this
    private OptionalLong lifecycleStamp = OptionalLong.empty();

    public DistributableSessionManager(DistributableSessionManagerConfiguration config) {
        this.deploymentName = config.getDeploymentName();
        this.manager = config.getSessionManager();
        this.listeners = config.getSessionListeners();
        this.statistics = config.getStatistics();
    }

    @Override
    public SessionListeners getSessionListeners() {
        return this.listeners;
    }

    @Override
    public SessionManager<Map<String, Object>, Batch> getSessionManager() {
        return this.manager;
    }

    @Override
    public synchronized void start() {
        this.lifecycleStamp.ifPresent(this);
        this.manager.start();
        if (this.statistics != null) {
            this.statistics.reset();
        }
    }

    @Override
    public void accept(long stamp) {
        this.lifecycleLock.unlock(stamp);
        this.lifecycleStamp = OptionalLong.empty();
    }

    @Override
    public synchronized void stop() {
        if (!this.lifecycleStamp.isPresent()) {
            Duration stopTimeout = this.manager.getStopTimeout();
            try {
                long stamp = this.lifecycleLock.tryWriteLock(stopTimeout.getSeconds(), TimeUnit.SECONDS);
                if (stamp != 0) {
                    this.lifecycleStamp = OptionalLong.of(stamp);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        this.manager.stop();
    }

    private Consumer<HttpServerExchange> getSessionCloseTask() {
        StampedLock lock = this.lifecycleLock;
        long stamp = lock.tryReadLock();
        if (stamp == 0L) {
            throw UndertowClusteringLogger.ROOT_LOGGER.sessionManagerStopped();
        }
        AttachmentKey<io.undertow.server.session.Session> key = this.key;
        AtomicLong stampRef = new AtomicLong(stamp);
        return new Consumer<>() {
            @Override
            public void accept(HttpServerExchange exchange) {
                try {
                    // Ensure we only unlock once.
                    long stamp = stampRef.getAndSet(0L);
                    if (stamp != 0L) {
                        lock.unlock(stamp);
                    }
                } finally {
                    if (exchange != null) {
                        exchange.removeAttachment(key);
                    }
                }
            }
        };
    }

    @Override
    public io.undertow.server.session.Session createSession(HttpServerExchange exchange, SessionConfig config) {
        if (config == null) {
            throw UndertowMessages.MESSAGES.couldNotFindSessionCookieConfig();
        }
        if (exchange.isResponseStarted()) { // Should match the condition in io.undertow.servlet.spec.HttpServletResponseImpl#isCommitted()
            // Return single-use session to be garbage collected at the end of the request
            io.undertow.server.session.Session session = new OrphanSession(this, this.manager.getIdentifierFactory().get());
            session.setMaxInactiveInterval(this.defaultSessionTimeout);
            return session;
        }

        String requestedId = config.findSessionId(exchange);

        boolean close = true;
        Consumer<HttpServerExchange> closeTask = this.getSessionCloseTask();
        try {
            String id = (requestedId == null) ? this.manager.getIdentifierFactory().get() : requestedId;

            Batcher<Batch> batcher = this.manager.getBatcher();
            // Batch will be closed by Session.close();
            Batch batch = batcher.createBatch();
            try {
                Session<Map<String, Object>> session = this.manager.createSession(id);
                if (session == null) {
                    throw UndertowClusteringLogger.ROOT_LOGGER.sessionAlreadyExists(id);
                }
                // Apply session ID encoding
                config.setSessionId(exchange, id);

                io.undertow.server.session.Session result = new DistributableSession(this, session, config, batcher.suspendBatch(), closeTask, this.statistics);
                this.listeners.sessionCreated(result, exchange);
                if (this.statistics != null) {
                    this.statistics.record(session.getMetaData());
                }
                exchange.putAttachment(this.key, result);
                close = false;
                return result;
            } catch (RuntimeException | Error e) {
                batch.discard();
                throw e;
            } finally {
                if (close) {
                    batch.close();
                }
            }
        } finally {
            if (close) {
                closeTask.accept(exchange);
            }
        }
    }

    @Override
    public io.undertow.server.session.Session getSession(HttpServerExchange exchange, SessionConfig config) {
        if (exchange != null) {
            io.undertow.server.session.Session attachedSession = exchange.getAttachment(this.key);
            if (attachedSession != null) {
                return attachedSession;
            }
        }

        if (config == null) {
            throw UndertowMessages.MESSAGES.couldNotFindSessionCookieConfig();
        }

        String id = config.findSessionId(exchange);
        if (id == null) {
            return null;
        }

        // If requested id contains invalid characters, then session cannot exist and would otherwise cause session lookup to fail
        if (!IDENTIFIER_MARSHALLER.validate(id)) {
            return null;
        }

        boolean close = true;
        Consumer<HttpServerExchange> closeTask = this.getSessionCloseTask();
        try {
            Batcher<Batch> batcher = this.manager.getBatcher();
            Batch batch = batcher.createBatch();
            try {
                Session<Map<String, Object>> session = this.manager.findSession(id);
                if (session == null) {
                    return null;
                }
                // Update session ID encoding
                config.setSessionId(exchange, id);

                io.undertow.server.session.Session result = new DistributableSession(this, session, config, batcher.suspendBatch(), closeTask, this.statistics);
                if (exchange != null) {
                    exchange.putAttachment(this.key, result);
                }
                close = false;
                return result;
            } catch (RuntimeException | Error e) {
                batch.discard();
                throw e;
            } finally {
                if (close) {
                    batch.close();
                }
            }
        } finally {
            if (close) {
                closeTask.accept(exchange);
            }
        }
    }

    @Override
    public void registerSessionListener(SessionListener listener) {
        this.listeners.addSessionListener(listener);
    }

    @Override
    public void removeSessionListener(SessionListener listener) {
        this.listeners.removeSessionListener(listener);
    }

    @Override
    public void setDefaultSessionTimeout(int timeout) {
        this.defaultSessionTimeout = timeout;
    }

    @Override
    public Set<String> getTransientSessions() {
        // We are a distributed session manager, so none of our sessions are transient
        return Collections.emptySet();
    }

    @Override
    public Set<String> getActiveSessions() {
        return this.manager.getActiveSessions();
    }

    @Override
    public Set<String> getAllSessions() {
        return this.manager.getLocalSessions();
    }

    @Override
    public io.undertow.server.session.Session getSession(String sessionId) {
        // If requested id contains invalid characters, then session cannot exist and would otherwise cause session lookup to fail
        if (!IDENTIFIER_MARSHALLER.validate(sessionId)) {
            return null;
        }
        Session<Map<String, Object>> session = new OOBSession<>(this.manager, sessionId, LocalSessionContextFactory.INSTANCE.get());
        return session.isValid() ? new DistributableSession(this, session, new SimpleSessionConfig(sessionId), null, Functions.discardingConsumer(), null) : null;
    }

    @Override
    public String getDeploymentName() {
        return this.deploymentName;
    }

    @Override
    public SessionManagerStatistics getStatistics() {
        return this.statistics;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof DistributableSessionManager)) return false;
        DistributableSessionManager manager = (DistributableSessionManager) object;
        return this.deploymentName.equals(manager.getDeploymentName());
    }

    @Override
    public int hashCode() {
        return this.deploymentName.hashCode();
    }

    @Override
    public String toString() {
        return this.deploymentName;
    }
}
