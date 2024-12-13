/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.ejb.infinispan.bean;

import static org.wildfly.clustering.cache.function.Functions.whenNullFunction;
import static org.wildfly.common.function.Functions.discardingConsumer;

import java.util.Map;
import java.util.concurrent.CompletionStage;

import org.infinispan.Cache;
import org.wildfly.clustering.cache.CacheEntryCreator;
import org.wildfly.clustering.cache.CacheEntryMutator;
import org.wildfly.clustering.cache.CacheEntryMutatorFactory;
import org.wildfly.clustering.cache.CacheEntryRemover;
import org.wildfly.clustering.cache.infinispan.embedded.EmbeddedCacheConfiguration;
import org.wildfly.clustering.cache.infinispan.embedded.EmbeddedCacheEntryMutatorFactory;
import org.wildfly.clustering.ejb.bean.BeanInstance;
import org.wildfly.clustering.ejb.cache.bean.BeanGroupKey;
import org.wildfly.clustering.marshalling.MarshalledValue;

/**
 * Manages the cache entry for a bean group.
 * @author Paul Ferraro
 * @param <K> the bean identifier type
 * @param <V> the bean instance type
 * @param <C> the marshalled value context type
 */
public class InfinispanBeanGroupManager<K, V extends BeanInstance<K>, C> implements CacheEntryCreator<K, MarshalledValue<Map<K, V>, C>, MarshalledValue<Map<K, V>, C>>, CacheEntryRemover<K>, CacheEntryMutatorFactory<K, MarshalledValue<Map<K, V>, C>> {

    private final Cache<BeanGroupKey<K>, MarshalledValue<Map<K, V>, C>> cache;
    private final Cache<BeanGroupKey<K>, MarshalledValue<Map<K, V>, C>> removeCache;
    private final CacheEntryMutatorFactory<BeanGroupKey<K>, MarshalledValue<Map<K, V>, C>> mutatorFactory;

    public InfinispanBeanGroupManager(EmbeddedCacheConfiguration configuration) {
        this.cache = configuration.getCache();
        this.removeCache = configuration.getWriteOnlyCache();
        this.mutatorFactory = new EmbeddedCacheEntryMutatorFactory<>(configuration.getCache());
    }

    @Override
    public CompletionStage<MarshalledValue<Map<K, V>, C>> createValueAsync(K id, MarshalledValue<Map<K, V>, C> defaultValue) {
        return this.cache.putIfAbsentAsync(new InfinispanBeanGroupKey<>(id), defaultValue).thenApply(whenNullFunction(defaultValue));
    }

    @Override
    public CompletionStage<Void> removeAsync(K id) {
        return this.removeCache.removeAsync(new InfinispanBeanGroupKey<>(id)).thenAccept(discardingConsumer());
    }

    @Override
    public CacheEntryMutator createMutator(K id, MarshalledValue<Map<K, V>, C> value) {
        return this.mutatorFactory.createMutator(new InfinispanBeanGroupKey<>(id), value);
    }
}
