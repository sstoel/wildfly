/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.clustering.singleton;

import java.util.function.UnaryOperator;

import org.jboss.as.clustering.controller.CapabilityReference;
import org.jboss.as.clustering.controller.ChildResourceDefinition;
import org.jboss.as.clustering.controller.CommonUnaryRequirement;
import org.jboss.as.clustering.controller.ResourceDescriptor;
import org.jboss.as.clustering.controller.ResourceServiceConfiguratorFactory;
import org.jboss.as.clustering.controller.ResourceServiceHandler;
import org.jboss.as.clustering.controller.SimpleResourceRegistrar;
import org.jboss.as.clustering.controller.SimpleResourceServiceHandler;
import org.jboss.as.clustering.controller.UnaryCapabilityNameResolver;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.wildfly.clustering.singleton.SingletonElectionPolicy;

/**
 * Definition of an election policy resource.
 * @author Paul Ferraro
 */
public abstract class ElectionPolicyResourceDefinition extends ChildResourceDefinition<ManagementResourceRegistration> {

    static final PathElement WILDCARD_PATH = pathElement(PathElement.WILDCARD_VALUE);

    static PathElement pathElement(String value) {
        return PathElement.pathElement("election-policy", value);
    }

    enum Capability implements org.jboss.as.clustering.controller.Capability {
        ELECTION_POLICY("org.wildfly.clustering.singleton-policy.election", SingletonElectionPolicy.class),
        ;
        private final RuntimeCapability<Void> definition;

        Capability(String name, Class<?> type) {
            this.definition = RuntimeCapability.Builder.of(name, true).setServiceType(type).setDynamicNameMapper(UnaryCapabilityNameResolver.PARENT).build();
        }

        @Override
        public RuntimeCapability<Void> getDefinition() {
            return this.definition;
        }
    }

    enum Attribute implements org.jboss.as.clustering.controller.Attribute, UnaryOperator<StringListAttributeDefinition.Builder> {
        NAME_PREFERENCES("name-preferences", "socket-binding-preferences"),
        SOCKET_BINDING_PREFERENCES("socket-binding-preferences", "name-preferences") {
            @Override
            public StringListAttributeDefinition.Builder apply(StringListAttributeDefinition.Builder builder) {
                return builder.setAllowExpression(false)
                        .setCapabilityReference(new CapabilityReference(Capability.ELECTION_POLICY, CommonUnaryRequirement.OUTBOUND_SOCKET_BINDING))
                        ;
            }
        }
        ;
        private final AttributeDefinition definition;

        Attribute(String name, String alternative) {
            this.definition = this.apply(new StringListAttributeDefinition.Builder(name).setAllowExpression(true))
                    .setAlternatives(alternative)
                    .setRequired(false)
                    .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();
        }

        @Override
        public AttributeDefinition getDefinition() {
            return this.definition;
        }

        @Override
        public StringListAttributeDefinition.Builder apply(StringListAttributeDefinition.Builder builder) {
            return builder;
        }
    }

    private final UnaryOperator<ResourceDescriptor> configurator;
    private final ResourceServiceConfiguratorFactory serviceConfiguratorFactory;

    ElectionPolicyResourceDefinition(PathElement path, ResourceDescriptionResolver resolver, UnaryOperator<ResourceDescriptor> configurator, ResourceServiceConfiguratorFactory serviceConfiguratorFactory) {
        super(path, resolver);
        this.configurator = configurator;
        this.serviceConfiguratorFactory = serviceConfiguratorFactory;
    }

    @Override
    public ManagementResourceRegistration register(ManagementResourceRegistration parent) {
        ManagementResourceRegistration registration = parent.registerSubModel(this);

        ResourceDescriptor descriptor = this.configurator.apply(new ResourceDescriptor(this.getResourceDescriptionResolver()))
                .addAttributes(ElectionPolicyResourceDefinition.Attribute.class)
                .addCapabilities(ElectionPolicyResourceDefinition.Capability.class)
                ;
        ResourceServiceHandler handler = new SimpleResourceServiceHandler(this.serviceConfiguratorFactory);
        new SimpleResourceRegistrar(descriptor, handler).register(registration);

        return registration;
    }
}
