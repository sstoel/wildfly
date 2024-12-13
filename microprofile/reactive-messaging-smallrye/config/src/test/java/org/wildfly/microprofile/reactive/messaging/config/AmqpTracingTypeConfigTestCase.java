/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.microprofile.reactive.messaging.config;

public class AmqpTracingTypeConfigTestCase extends TracingTypeConfigTest {

    public AmqpTracingTypeConfigTestCase() {
        super("smallrye-amqp");
    }

    @Override
    protected void setInterceptorFactoryTracingType(TracingType tracingType) {
        TracingTypeInterceptorFactory.AMQP_TRACING_TYPE = tracingType;
    }
}
