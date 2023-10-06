/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.clustering.singleton;

import java.util.EnumSet;
import java.util.function.Consumer;

import org.jboss.as.clustering.controller.CapabilityProvider;
import org.jboss.as.clustering.controller.CapabilityReference;
import org.jboss.as.clustering.controller.DeploymentChainContributingResourceRegistrar;
import org.jboss.as.clustering.controller.ManagementResourceRegistration;
import org.jboss.as.clustering.controller.RequirementCapability;
import org.jboss.as.clustering.controller.ResourceDescriptor;
import org.jboss.as.clustering.controller.ResourceServiceHandler;
import org.jboss.as.clustering.controller.SubsystemRegistration;
import org.jboss.as.clustering.controller.SubsystemResourceDefinition;
import org.jboss.as.controller.CapabilityReferenceRecorder;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.registry.AttributeAccess.Flag;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.as.server.deployment.Phase;
import org.jboss.as.server.deployment.jbossallxml.JBossAllSchema;
import org.jboss.dmr.ModelType;
import org.wildfly.clustering.service.Requirement;
import org.wildfly.clustering.singleton.SingletonDefaultRequirement;
import org.wildfly.clustering.singleton.SingletonRequirement;
import org.wildfly.extension.clustering.singleton.deployment.SingletonDeploymentDependencyProcessor;
import org.wildfly.extension.clustering.singleton.deployment.SingletonDeploymentParsingProcessor;
import org.wildfly.extension.clustering.singleton.deployment.SingletonDeploymentProcessor;
import org.wildfly.extension.clustering.singleton.deployment.SingletonDeploymentSchema;

/**
 * Definition of the singleton deployer resource.
 * @author Paul Ferraro
 */
public class SingletonResourceDefinition extends SubsystemResourceDefinition implements Consumer<DeploymentProcessorTarget> {

    static final PathElement PATH = pathElement(SingletonExtension.SUBSYSTEM_NAME);

    enum Capability implements CapabilityProvider {
        @Deprecated DEFAULT_LEGACY_POLICY(SingletonDefaultRequirement.SINGLETON_POLICY),
        DEFAULT_POLICY(SingletonDefaultRequirement.POLICY),
        ;
        private final org.jboss.as.clustering.controller.Capability capability;

        Capability(Requirement requirement) {
            this.capability = new RequirementCapability(requirement);
        }

        @Override
        public org.jboss.as.clustering.controller.Capability getCapability() {
            return this.capability;
        }
    }

    enum Attribute implements org.jboss.as.clustering.controller.Attribute {
        DEFAULT("default", ModelType.STRING, new CapabilityReference(Capability.DEFAULT_POLICY, SingletonRequirement.POLICY)),
        ;
        private final SimpleAttributeDefinition definition;

        Attribute(String name, ModelType type, CapabilityReferenceRecorder reference) {
            this.definition = new SimpleAttributeDefinitionBuilder(name, type)
                    .setCapabilityReference(reference)
                    .setFlags(Flag.RESTART_RESOURCE_SERVICES)
                    .build();
        }

        @Override
        public SimpleAttributeDefinition getDefinition() {
            return this.definition;
        }
    }

    SingletonResourceDefinition() {
        super(PATH, SingletonExtension.SUBSYSTEM_RESOLVER);
    }

    @Override
    public void register(SubsystemRegistration parentRegistration) {
        ManagementResourceRegistration registration = parentRegistration.registerSubsystemModel(this);

        registration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);

        ResourceDescriptor descriptor = new ResourceDescriptor(this.getResourceDescriptionResolver())
                .addAttributes(Attribute.class)
                .addCapabilities(Capability.class)
                ;
        ResourceServiceHandler handler = new SingletonServiceHandler();
        new DeploymentChainContributingResourceRegistrar(descriptor, handler, this).register(registration);

        new SingletonPolicyResourceDefinition().register(registration);
    }

    @Override
    public void accept(DeploymentProcessorTarget target) {
        target.addDeploymentProcessor(SingletonExtension.SUBSYSTEM_NAME, Phase.STRUCTURE, Phase.STRUCTURE_REGISTER_JBOSS_ALL_SINGLETON_DEPLOYMENT, JBossAllSchema.createDeploymentUnitProcessor(EnumSet.allOf(SingletonDeploymentSchema.class), SingletonDeploymentDependencyProcessor.CONFIGURATION_KEY));
        target.addDeploymentProcessor(SingletonExtension.SUBSYSTEM_NAME, Phase.PARSE, Phase.PARSE_SINGLETON_DEPLOYMENT, new SingletonDeploymentParsingProcessor());
        target.addDeploymentProcessor(SingletonExtension.SUBSYSTEM_NAME, Phase.DEPENDENCIES, Phase.DEPENDENCIES_SINGLETON_DEPLOYMENT, new SingletonDeploymentDependencyProcessor());
        target.addDeploymentProcessor(SingletonExtension.SUBSYSTEM_NAME, Phase.CONFIGURE_MODULE, Phase.CONFIGURE_SINGLETON_DEPLOYMENT, new SingletonDeploymentProcessor());
    }
}
