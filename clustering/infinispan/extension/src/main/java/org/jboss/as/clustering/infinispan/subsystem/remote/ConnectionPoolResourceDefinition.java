/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.clustering.infinispan.subsystem.remote;

import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

import org.infinispan.client.hotrod.configuration.ExhaustedAction;
import org.jboss.as.clustering.controller.ManagementResourceRegistration;
import org.jboss.as.clustering.controller.ResourceDescriptor;
import org.jboss.as.clustering.controller.ResourceServiceHandler;
import org.jboss.as.clustering.controller.SimpleResourceRegistrar;
import org.jboss.as.clustering.controller.SimpleResourceServiceHandler;
import org.jboss.as.clustering.infinispan.subsystem.ComponentResourceDefinition;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * /subsystem=infinispan/remote-cache-container=X/component=connection-pool
 *
 * @author Radoslav Husar
 */
public class ConnectionPoolResourceDefinition extends ComponentResourceDefinition {

    public static final PathElement PATH = pathElement("connection-pool");

    public enum Attribute implements org.jboss.as.clustering.controller.Attribute, UnaryOperator<SimpleAttributeDefinitionBuilder> {
        EXHAUSTED_ACTION("exhausted-action", ModelType.STRING, new ModelNode(ExhaustedAction.WAIT.name())) {
            @Override
            public SimpleAttributeDefinitionBuilder apply(SimpleAttributeDefinitionBuilder builder) {
                return builder.setValidator(EnumValidator.create(ExhaustedAction.class));
            }
        },
        MAX_ACTIVE("max-active", ModelType.INT, null),
        MAX_WAIT("max-wait", ModelType.LONG, null),
        MIN_EVICTABLE_IDLE_TIME("min-evictable-idle-time", ModelType.LONG, new ModelNode(TimeUnit.MINUTES.toMillis(30))),
        MIN_IDLE("min-idle", ModelType.INT, new ModelNode(1)),
        ;

        private final AttributeDefinition definition;

        Attribute(String name, ModelType type, ModelNode defaultValue) {
            this.definition = this.apply(new SimpleAttributeDefinitionBuilder(name, type)
                    .setAllowExpression(true)
                    .setRequired(false)
                    .setDefaultValue(defaultValue)
                    .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .setMeasurementUnit((type == ModelType.LONG) ? MeasurementUnit.MILLISECONDS : null)
                    ).build();
        }

        @Override
        public AttributeDefinition getDefinition() {
            return this.definition;
        }

        @Override
        public SimpleAttributeDefinitionBuilder apply(SimpleAttributeDefinitionBuilder builder) {
            return builder;
        }
    }

    ConnectionPoolResourceDefinition() {
        super(PATH);
    }

    @Override
    public ManagementResourceRegistration register(ManagementResourceRegistration parent) {
        ManagementResourceRegistration registration = parent.registerSubModel(this);

        ResourceDescriptor descriptor = new ResourceDescriptor(this.getResourceDescriptionResolver())
                .addAttributes(Attribute.class)
                ;
        ResourceServiceHandler handler = new SimpleResourceServiceHandler(ConnectionPoolServiceConfigurator::new);
        new SimpleResourceRegistrar(descriptor, handler).register(registration);

        return registration;
    }
}
