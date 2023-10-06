/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.web.cache.session;

import java.time.Duration;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.wildfly.clustering.ee.Batch;
import org.wildfly.clustering.ee.Batcher;
import org.wildfly.clustering.ee.Manager;
import org.wildfly.clustering.ee.ManagerFactory;
import org.wildfly.clustering.web.session.ImmutableSession;
import org.wildfly.clustering.web.session.Session;
import org.wildfly.clustering.web.session.SessionAttributes;
import org.wildfly.clustering.web.session.SessionManager;
import org.wildfly.clustering.web.session.SessionMetaData;
import org.wildfly.common.function.Functions;

/**
 * A concurrent session manager, that can share session references across concurrent threads.
 * @author Paul Ferraro
 */
public class ConcurrentSessionManager<L, B extends Batch> implements SessionManager<L, B> {

    private final SessionManager<L, B> manager;
    private final Manager<String, Session<L>> concurrentManager;

    public ConcurrentSessionManager(SessionManager<L, B> manager, ManagerFactory<String, Session<L>> concurrentManagerFactory) {
        this.manager = manager;
        this.concurrentManager = concurrentManagerFactory.apply(Functions.discardingConsumer(), new Consumer<Session<L>>() {
            @Override
            public void accept(Session<L> session) {
                ((ConcurrentSession<L>) session).closeSession();
            }
        });
    }

    @Override
    public Session<L> findSession(String id) {
        SessionManager<L, B> manager = this.manager;
        Function<Runnable, Session<L>> factory = new Function<>() {
            @Override
            public ConcurrentSession<L> apply(Runnable closeTask) {
                Session<L> session = manager.findSession(id);
                return (session != null) ? new ConcurrentSession<>(session, closeTask) : null;
            }
        };
        @SuppressWarnings("resource")
        Session<L> session = this.concurrentManager.apply(id, factory);
        // If session was invalidated by a concurrent thread, return null instead of an invalid session
        // This will reduce the likelihood that a duplicate invalidation request (e.g. from a double-clicked logout) results in an ISE
        if (session != null && !session.isValid()) {
            session.close();
            return null;
        }
        return session;
    }

    @Override
    public Session<L> createSession(String id) {
        SessionManager<L, B> manager = this.manager;
        Function<Runnable, Session<L>> factory = new Function<>() {
            @Override
            public ConcurrentSession<L> apply(Runnable closeTask) {
                Session<L> session = manager.createSession(id);
                return new ConcurrentSession<>(session, closeTask);
            }
        };
        return this.concurrentManager.apply(id, factory);
    }

    @Override
    public Supplier<String> getIdentifierFactory() {
        return this.manager.getIdentifierFactory();
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
    public long getActiveSessionCount() {
        return this.manager.getActiveSessionCount();
    }

    @Override
    public Batcher<B> getBatcher() {
        return this.manager.getBatcher();
    }

    @Override
    public Set<String> getActiveSessions() {
        return this.manager.getActiveSessions();
    }

    @Override
    public Set<String> getLocalSessions() {
        return this.manager.getLocalSessions();
    }

    @Override
    public ImmutableSession readSession(String id) {
        return this.manager.readSession(id);
    }

    @Override
    public Duration getStopTimeout() {
        return this.manager.getStopTimeout();
    }

    private static class ConcurrentSession<L> implements Session<L> {
        private final Session<L> session;
        private final Runnable closeTask;

        ConcurrentSession(Session<L> session, Runnable closeTask) {
            this.session = session;
            this.closeTask = closeTask;
        }

        void closeSession() {
            this.session.close();
        }

        @Override
        public String getId() {
            return this.session.getId();
        }

        @Override
        public boolean isValid() {
            return this.session.isValid();
        }

        @Override
        public SessionMetaData getMetaData() {
            return this.session.getMetaData();
        }

        @Override
        public SessionAttributes getAttributes() {
            return this.session.getAttributes();
        }

        @Override
        public void invalidate() {
            this.session.invalidate();
            this.closeTask.run();
        }

        @Override
        public void close() {
            this.closeTask.run();
        }

        @Override
        public L getLocalContext() {
            return this.session.getLocalContext();
        }
    }
}
