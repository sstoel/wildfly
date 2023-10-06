/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.undertow.session;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import io.undertow.server.session.PathParameterSessionConfig;
import io.undertow.server.session.SessionConfig;
import io.undertow.server.session.SessionCookieConfig;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.api.SessionConfigWrapper;
import org.jboss.as.web.session.AffinityLocator;
import org.wildfly.extension.undertow.CookieConfig;

/**
 * Adds affinity locator handling to a {@link SessionConfig}.
 *
 * @author Radoslav Husar
 */
public class AffinitySessionConfigWrapper implements SessionConfigWrapper {

    private final Map<SessionConfig.SessionCookieSource, SessionConfig> affinityConfigMap = new EnumMap<>(SessionConfig.SessionCookieSource.class);
    private final AffinityLocator locator;

    public AffinitySessionConfigWrapper(CookieConfig config, AffinityLocator locator) {
        this.locator = locator;

        // Setup SessionCookieSource->SessionConfig mapping:

        // SessionConfig.SessionCookieSource.COOKIE
        SessionCookieConfig cookieSessionConfig = new SessionCookieConfig();

        cookieSessionConfig.setCookieName(config.getName());
        if (config.getDomain() != null) {
            cookieSessionConfig.setDomain(config.getDomain());
        }
        if (config.getHttpOnly() != null) {
            cookieSessionConfig.setHttpOnly(config.getHttpOnly());
        }
        if (config.getSecure() != null) {
            cookieSessionConfig.setSecure(config.getSecure());
        }
        if (config.getMaxAge() != null) {
            cookieSessionConfig.setMaxAge(config.getMaxAge());
        }

        affinityConfigMap.put(SessionConfig.SessionCookieSource.COOKIE, cookieSessionConfig);

        // SessionConfig.SessionCookieSource.URL

        // In this case use the cookie name as the path parameter
        String pathParameterName = config.getName().toLowerCase(Locale.ENGLISH);
        PathParameterSessionConfig pathParameterSessionConfig = new PathParameterSessionConfig(pathParameterName);
        affinityConfigMap.put(SessionConfig.SessionCookieSource.URL, pathParameterSessionConfig);
    }

    @Override
    public SessionConfig wrap(SessionConfig sessionConfig, Deployment deployment) {
        return new AffinitySessionConfig(sessionConfig, this.affinityConfigMap, this.locator);
    }
}
