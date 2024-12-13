/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.web.undertow.session;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.wildfly.clustering.session.ImmutableSession;
import org.wildfly.clustering.session.ImmutableSessionMetaData;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionConfig;
import io.undertow.server.session.SessionManager;

/**
 * Undertow adapter for an {@link ImmutableSession}.
 * @author Paul Ferraro
 */
public class DistributableImmutableSession implements Session {

    private final SessionManager manager;
    private final String id;
    private final Map<String, Object> attributes;
    private final long creationTime;
    private final long lastAccessedTime;
    private final int maxInactiveInterval;

    public DistributableImmutableSession(SessionManager manager, ImmutableSession session) {
        this.manager = manager;
        this.id = session.getId();
        this.attributes = Map.copyOf(session.getAttributes());
        ImmutableSessionMetaData metaData = session.getMetaData();
        this.creationTime = metaData.getCreationTime().toEpochMilli();
        this.lastAccessedTime = metaData.getLastAccessStartTime().toEpochMilli();
        this.maxInactiveInterval = (int) metaData.getTimeout().getSeconds();
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public SessionManager getSessionManager() {
        return this.manager;
    }

    @Override
    public void requestDone(HttpServerExchange serverExchange) {
        // Do nothing
    }

    @Override
    public long getCreationTime() {
        return this.creationTime;
    }

    @Override
    public long getLastAccessedTime() {
        return this.lastAccessedTime;
    }

    @Override
    public int getMaxInactiveInterval() {
        return this.maxInactiveInterval;
    }

    @Override
    public void setMaxInactiveInterval(int interval) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getAttribute(String name) {
        return this.attributes.get(name);
    }

    @Override
    public Set<String> getAttributeNames() {
        return Collections.unmodifiableSet(this.attributes.keySet());
    }

    @Override
    public Object setAttribute(String name, Object value) {
        return value;
    }

    @Override
    public Object removeAttribute(String name) {
        return null;
    }

    @Override
    public void invalidate(HttpServerExchange exchange) {
        // Do nothing
    }

    @Override
    public String changeSessionId(HttpServerExchange exchange, SessionConfig config) {
        return null;
    }
}
