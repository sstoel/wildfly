/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.clustering.infinispan.subsystem;

import java.util.EnumSet;
import java.util.function.UnaryOperator;

import org.infinispan.conflict.MergePolicy;
import org.infinispan.partitionhandling.PartitionHandling;
import org.jboss.as.clustering.controller.AttributeTranslation;
import org.jboss.as.clustering.controller.AttributeValueTranslator;
import org.jboss.as.clustering.controller.ManagementResourceRegistration;
import org.jboss.as.clustering.controller.ResourceDescriptor;
import org.jboss.as.clustering.controller.ResourceServiceHandler;
import org.jboss.as.clustering.controller.SimpleResourceRegistrar;
import org.jboss.as.clustering.controller.SimpleResourceServiceHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Resource definition of the partition handling component of a cache.
 * @author Paul Ferraro
 */
public class PartitionHandlingResourceDefinition extends ComponentResourceDefinition {

    static final PathElement PATH = pathElement("partition-handling");

    enum Attribute implements org.jboss.as.clustering.controller.Attribute {
        WHEN_SPLIT("when-split", PartitionHandling.ALLOW_READ_WRITES, EnumValidator.create(PartitionHandling.class)),
        MERGE_POLICY("merge-policy", MergePolicy.NONE, EnumValidator.create(MergePolicy.class, EnumSet.complementOf(EnumSet.of(MergePolicy.CUSTOM)))),
        ;
        private final AttributeDefinition definition;

        <E extends Enum<E>> Attribute(String name, E defaultValue, EnumValidator<E> validator) {
            this(name, ModelType.STRING, new ModelNode(defaultValue.name()), builder -> builder.setValidator(validator));
        }

        Attribute(String name, ModelType type, ModelNode defaultValue, UnaryOperator<SimpleAttributeDefinitionBuilder> configurator) {
            this.definition = configurator.apply(new SimpleAttributeDefinitionBuilder(name, type)
                    .setAllowExpression(true)
                    .setDefaultValue(defaultValue)
                    .setRequired(false)
                    .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    ).build()
                    ;
        }

        @Override
        public AttributeDefinition getDefinition() {
            return this.definition;
        }
    }

    enum DeprecatedAttribute implements org.jboss.as.clustering.controller.Attribute {
        ENABLED("enabled", ModelType.BOOLEAN, ModelNode.FALSE, InfinispanSubsystemModel.VERSION_16_0_0),
        ;
        private final AttributeDefinition definition;

        DeprecatedAttribute(String name, ModelType type, ModelNode defaultValue, InfinispanSubsystemModel deprecation) {
            this.definition = new SimpleAttributeDefinitionBuilder(name, type)
                    .setAllowExpression(true)
                    .setRequired(false)
                    .setDefaultValue(defaultValue)
                    .setDeprecated(deprecation.getVersion())
                    .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();
        }

        @Override
        public AttributeDefinition getDefinition() {
            return this.definition;
        }
    }

    PartitionHandlingResourceDefinition() {
        super(PATH);
    }

    @Override
    public ManagementResourceRegistration register(ManagementResourceRegistration parent) {
        ManagementResourceRegistration registration = parent.registerSubModel(this);

        ResourceDescriptor descriptor = new ResourceDescriptor(this.getResourceDescriptionResolver())
                .addAttributes(Attribute.class)
                .addAttributeTranslation(DeprecatedAttribute.ENABLED, new AttributeTranslation() {
                    @Override
                    public org.jboss.as.clustering.controller.Attribute getTargetAttribute() {
                        return Attribute.WHEN_SPLIT;
                    }

                    @Override
                    public AttributeValueTranslator getReadTranslator() {
                        return (context, value) -> value.isDefined() ? (value.equals(Attribute.WHEN_SPLIT.getDefinition().getDefaultValue()) ? ModelNode.FALSE : ModelNode.TRUE) : value;
                    }

                    @Override
                    public AttributeValueTranslator getWriteTranslator() {
                        return (context, value) -> value.isDefined() ? new ModelNode((value.asBoolean() ? PartitionHandling.DENY_READ_WRITES : PartitionHandling.ALLOW_READ_WRITES).name()) : value;
                    }
                })
                ;
        ResourceServiceHandler handler = new SimpleResourceServiceHandler(PartitionHandlingServiceConfigurator::new);
        new SimpleResourceRegistrar(descriptor, handler).register(registration);

        return registration;
    }
}
