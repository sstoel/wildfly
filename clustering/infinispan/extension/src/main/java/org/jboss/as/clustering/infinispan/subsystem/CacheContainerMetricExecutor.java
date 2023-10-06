/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.clustering.infinispan.subsystem;

import java.util.function.Function;

import org.infinispan.manager.EmbeddedCacheManager;
import org.jboss.as.clustering.controller.Metric;
import org.jboss.as.clustering.controller.MetricExecutor;
import org.jboss.as.clustering.controller.MetricFunction;
import org.jboss.as.clustering.controller.FunctionExecutor;
import org.jboss.as.clustering.controller.FunctionExecutorRegistry;
import org.jboss.as.clustering.controller.UnaryCapabilityNameResolver;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.wildfly.clustering.infinispan.service.InfinispanRequirement;

/**
 * Executor for cache-container metrics.
 *
 * @author Paul Ferraro
 */
public class CacheContainerMetricExecutor implements MetricExecutor<EmbeddedCacheManager> {

    private final FunctionExecutorRegistry<EmbeddedCacheManager> executors;

    public CacheContainerMetricExecutor(FunctionExecutorRegistry<EmbeddedCacheManager> executors) {
        this.executors = executors;
    }

    @Override
    public ModelNode execute(OperationContext context, Metric<EmbeddedCacheManager> metric) throws OperationFailedException {
        ServiceName name = InfinispanRequirement.CONTAINER.getServiceName(context, UnaryCapabilityNameResolver.DEFAULT);
        FunctionExecutor<EmbeddedCacheManager> executor = this.executors.get(name);
        return (executor != null) ? executor.execute(new MetricFunction<>(Function.identity(), metric)) : null;
    }
}
