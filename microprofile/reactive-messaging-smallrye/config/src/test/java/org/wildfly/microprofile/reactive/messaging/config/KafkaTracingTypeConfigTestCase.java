/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.microprofile.reactive.messaging.config;

public class KafkaTracingTypeConfigTestCase extends TracingTypeConfigTest {
    public KafkaTracingTypeConfigTestCase() {
        super("smallrye-kafka");
    }

    protected void setInterceptorFactoryTracingType(TracingType tracingType) {
        TracingTypeInterceptorFactory.KAFKA_TRACING_TYPE = tracingType;
    }
}
