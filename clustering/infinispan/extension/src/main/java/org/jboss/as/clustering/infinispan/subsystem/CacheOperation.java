/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.clustering.infinispan.subsystem;

import org.infinispan.interceptors.impl.CacheMgmtInterceptor;
import org.jboss.as.clustering.controller.Operation;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Paul Ferraro
 */
public enum CacheOperation implements Operation<CacheMgmtInterceptor> {

    RESET_STATISTICS("reset-statistics", ModelType.UNDEFINED) {
        @Override
        public ModelNode execute(ExpressionResolver resolver, ModelNode operation, CacheMgmtInterceptor interceptor) {
            interceptor.resetStatistics();
            return null;
        }
    },
    ;
    private final OperationDefinition definition;

    CacheOperation(String name, ModelType returnType) {
        this.definition = new SimpleOperationDefinitionBuilder(name, InfinispanExtension.SUBSYSTEM_RESOLVER.createChildResolver(CacheRuntimeResourceDefinition.WILDCARD_PATH))
                .setReplyType(returnType)
                .setRuntimeOnly()
                .build();
    }

    @Override
    public OperationDefinition getDefinition() {
        return this.definition;
    }
}
