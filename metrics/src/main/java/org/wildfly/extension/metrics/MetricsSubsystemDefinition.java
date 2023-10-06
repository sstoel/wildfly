/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.metrics;

import java.util.Arrays;
import java.util.Collection;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.ServiceRemoveStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceName;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2018 Red Hat inc.
 */
public class MetricsSubsystemDefinition extends PersistentResourceDefinition {

    static final String CLIENT_FACTORY_CAPABILITY ="org.wildfly.management.model-controller-client-factory";
    static final String MANAGEMENT_EXECUTOR ="org.wildfly.management.executor";
    static final String PROCESS_STATE_NOTIFIER = "org.wildfly.management.process-state-notifier";
    static final String HTTP_EXTENSIBILITY_CAPABILITY = "org.wildfly.management.http.extensible";
    public static final String METRICS_HTTP_SECURITY_CAPABILITY = "org.wildfly.extension.metrics.http-context.security-enabled";
    public static final String METRICS_SCAN_CAPABILITY = "org.wildfly.extension.metrics.scan";

    private static final RuntimeCapability<Void> METRICS_COLLECTOR_RUNTIME_CAPABILITY = RuntimeCapability.Builder.of("org.wildfly.extension.metrics.wildfly-collector", MetricCollector.class)
            .addRequirements(CLIENT_FACTORY_CAPABILITY, MANAGEMENT_EXECUTOR, PROCESS_STATE_NOTIFIER)
            .build();

    static final RuntimeCapability<Void> METRICS_HTTP_CONTEXT_CAPABILITY = RuntimeCapability.Builder.of("org.wildfly.extension.metrics.http-context", MetricsContextService.class)
            .addRequirements(HTTP_EXTENSIBILITY_CAPABILITY)
            .build();

    public static final RuntimeCapability<Void> METRICS_REGISTRY_RUNTIME_CAPABILITY = RuntimeCapability.Builder.of("org.wildfly.extension.metrics.registry", WildFlyMetricRegistry.class)
            .build();
    static final RuntimeCapability METRICS_CAPABILITY =
            RuntimeCapability.Builder.of("org.wildfly.management.http-context.metrics").build();

    public static final ServiceName WILDFLY_COLLECTOR = METRICS_COLLECTOR_RUNTIME_CAPABILITY.getCapabilityServiceName();

    static final AttributeDefinition SECURITY_ENABLED = SimpleAttributeDefinitionBuilder.create("security-enabled", ModelType.BOOLEAN)
            .setDefaultValue(ModelNode.TRUE)
            .setRequired(false)
            .setRestartAllServices()
            .setAllowExpression(true)
            .build();

    static final StringListAttributeDefinition EXPOSED_SUBSYSTEMS = new StringListAttributeDefinition.Builder("exposed-subsystems")
            .setRequired(false)
            .setRestartAllServices()
            .build();

    static final AttributeDefinition PREFIX = SimpleAttributeDefinitionBuilder.create("prefix", ModelType.STRING)
            .setRequired(false)
            .setRestartAllServices()
            .setAllowExpression(true)
            .build();

    static final AttributeDefinition[] ATTRIBUTES = { SECURITY_ENABLED, EXPOSED_SUBSYSTEMS, PREFIX };

    protected MetricsSubsystemDefinition() {
        super(new SimpleResourceDefinition.Parameters(MetricsExtension.SUBSYSTEM_PATH,
                MetricsExtension.getResourceDescriptionResolver(MetricsExtension.SUBSYSTEM_NAME))
                .setAddHandler(MetricsSubsystemAdd.INSTANCE)
                .setRemoveHandler(new ServiceRemoveStepHandler(MetricsSubsystemAdd.INSTANCE))
                .addCapabilities(METRICS_COLLECTOR_RUNTIME_CAPABILITY, METRICS_HTTP_CONTEXT_CAPABILITY,
                        METRICS_REGISTRY_RUNTIME_CAPABILITY, METRICS_CAPABILITY));
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Arrays.asList(ATTRIBUTES);
    }

}
