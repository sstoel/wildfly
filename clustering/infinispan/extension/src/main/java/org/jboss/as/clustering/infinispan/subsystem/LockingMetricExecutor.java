/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.clustering.infinispan.subsystem;

import org.infinispan.Cache;
import org.infinispan.util.concurrent.locks.impl.DefaultLockManager;
import org.jboss.as.clustering.controller.BinaryCapabilityNameResolver;
import org.jboss.as.clustering.controller.FunctionExecutorRegistry;

/**
 * A handler for cache locking metrics.
 *
 * @author Paul Ferraro
 */
public class LockingMetricExecutor extends CacheMetricExecutor<DefaultLockManager> {

    public LockingMetricExecutor(FunctionExecutorRegistry<Cache<?, ?>> executors) {
        super(executors, BinaryCapabilityNameResolver.GRANDPARENT_PARENT);
    }

    @Override
    public DefaultLockManager apply(Cache<?, ?> cache) {
        return (DefaultLockManager) cache.getAdvancedCache().getLockManager();
    }
}
