/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.web.undertow.session;

import java.util.Map;

import org.wildfly.clustering.ee.Batch;
import org.wildfly.clustering.web.session.SessionManager;

import io.undertow.server.session.SessionListeners;

/**
 * Exposes additional session manager aspects to a session.
 * @author Paul Ferraro
 */
public interface UndertowSessionManager extends io.undertow.server.session.SessionManager {
    /**
     * Returns the configured session listeners for this web application
     * @return the session listeners
     */
    SessionListeners getSessionListeners();

    /**
     * Returns underlying distributable session manager implementation.
     * @return a session manager
     */
    SessionManager<Map<String, Object>, Batch> getSessionManager();
}
