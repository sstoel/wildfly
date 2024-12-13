/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.ejb.infinispan.timer;

import java.util.function.Supplier;

import org.wildfly.clustering.cache.infinispan.embedded.EmbeddedCacheConfiguration;
import org.wildfly.clustering.ejb.cache.timer.RemappableTimerMetaDataEntry;
import org.wildfly.clustering.ejb.cache.timer.TimerFactory;
import org.wildfly.clustering.ejb.timer.TimerRegistry;
import org.wildfly.clustering.marshalling.Marshaller;
import org.wildfly.clustering.server.infinispan.dispatcher.CacheContainerCommandDispatcherFactory;

/**
 * @author Paul Ferraro
 */
public interface InfinispanTimerManagerConfiguration<I, C> extends EmbeddedCacheConfiguration {

    TimerFactory<I, RemappableTimerMetaDataEntry<C>> getTimerFactory();
    TimerRegistry<I> getRegistry();
    Marshaller<Object, C> getMarshaller();
    Supplier<I> getIdentifierFactory();
    CacheContainerCommandDispatcherFactory getCommandDispatcherFactory();
}
