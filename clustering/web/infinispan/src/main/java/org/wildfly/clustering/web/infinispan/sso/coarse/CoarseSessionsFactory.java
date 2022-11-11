/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.wildfly.clustering.web.infinispan.sso.coarse;

import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.infinispan.Cache;
import org.wildfly.clustering.ee.Mutator;
import org.wildfly.clustering.ee.infinispan.CacheEntryMutator;
import org.wildfly.clustering.ee.infinispan.InfinispanConfiguration;
import org.wildfly.clustering.web.cache.sso.SessionsFactory;
import org.wildfly.clustering.web.cache.sso.coarse.CoarseSessions;
import org.wildfly.clustering.web.cache.sso.coarse.SessionFilter;
import org.wildfly.clustering.web.sso.Sessions;

/**
 * @author Paul Ferraro
 */
public class CoarseSessionsFactory<D, S> implements SessionsFactory<Map<D, S>, D, S> {

    private final SessionsFilter<D, S> filter = new SessionsFilter<>();
    private final Cache<CoarseSessionsKey, Map<D, S>> cache;
    private final Cache<CoarseSessionsKey, Map<D, S>> createCache;

    public CoarseSessionsFactory(InfinispanConfiguration configuration) {
        this.cache = configuration.getCache();
        this.createCache = configuration.getWriteOnlyCache();
    }

    @Override
    public Sessions<D, S> createSessions(String ssoId, Map<D, S> value) {
        CoarseSessionsKey key = new CoarseSessionsKey(ssoId);
        Mutator mutator = new CacheEntryMutator<>(this.cache, key, value);
        return new CoarseSessions<>(value, mutator);
    }

    @Override
    public Map<D, S> createValue(String id, Void context) {
        Map<D, S> sessions = new ConcurrentHashMap<>();
        this.createCache.put(new CoarseSessionsKey(id), sessions);
        return sessions;
    }

    @Override
    public Map<D, S> findValue(String id) {
        return this.cache.get(new CoarseSessionsKey(id));
    }

    @Override
    public Map.Entry<String, Map<D, S>> findEntryContaining(S session) {
        SessionFilter<CoarseSessionsKey, D, S> filter = new SessionFilter<>(session);
        // Erase type to handle compilation issues with generics
        // Our filter will handle type safety and casting
        @SuppressWarnings("rawtypes")
        Cache cache = this.cache;
        try (Stream<Map.Entry<?, ?>> stream = cache.entrySet().stream()) {
            Map.Entry<CoarseSessionsKey, Map<D, S>> entry = stream.filter(this.filter).map(this.filter).filter(filter).findAny().orElse(null);
            return (entry != null) ? new AbstractMap.SimpleImmutableEntry<>(entry.getKey().getId(), entry.getValue()) : null;
        }
    }

    @Override
    public boolean remove(String id) {
        return this.cache.getAdvancedCache().remove(new CoarseSessionsKey(id)) != null;
    }
}
