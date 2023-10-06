/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.clustering.infinispan.subsystem;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import org.jboss.as.clustering.controller.Capability;
import org.jboss.as.clustering.controller.DeploymentChainContributingResourceRegistrar;
import org.jboss.as.clustering.controller.ManagementResourceRegistration;
import org.jboss.as.clustering.controller.RequirementCapability;
import org.jboss.as.clustering.controller.ResourceDescriptor;
import org.jboss.as.clustering.controller.ResourceServiceHandler;
import org.jboss.as.clustering.controller.SubsystemRegistration;
import org.jboss.as.clustering.controller.SubsystemResourceDefinition;
import org.jboss.as.clustering.controller.UnaryCapabilityNameResolver;
import org.jboss.as.clustering.controller.UnaryRequirementCapability;
import org.jboss.as.clustering.infinispan.deployment.ClusteringDependencyProcessor;
import org.jboss.as.clustering.infinispan.subsystem.remote.RemoteCacheContainerResourceDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.as.server.deployment.Phase;
import org.wildfly.clustering.server.service.ClusteringRequirement;

/**
 * The root resource of the Infinispan subsystem.
 *
 * @author Richard Achmatowicz (c) 2011 Red Hat Inc.
 */
public class InfinispanSubsystemResourceDefinition extends SubsystemResourceDefinition implements Consumer<DeploymentProcessorTarget> {

    static final PathElement PATH = pathElement(InfinispanExtension.SUBSYSTEM_NAME);

    InfinispanSubsystemResourceDefinition() {
        super(PATH, InfinispanExtension.SUBSYSTEM_RESOLVER);
    }

    @Override
    public void register(SubsystemRegistration parentRegistration) {
        ManagementResourceRegistration registration = parentRegistration.registerSubsystemModel(this);

        registration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);

        Set<ClusteringRequirement> requirements = EnumSet.allOf(ClusteringRequirement.class);
        List<Capability> capabilities = new ArrayList<>(requirements.size());
        List<Capability> localCapabilities = new ArrayList<>(requirements.size());
        UnaryOperator<RuntimeCapability.Builder<Void>> configurator = builder -> builder.setAllowMultipleRegistrations(true);
        for (ClusteringRequirement requirement : requirements) {
            capabilities.add(new RequirementCapability(requirement.getDefaultRequirement(), configurator));
            localCapabilities.add(new UnaryRequirementCapability(requirement, UnaryCapabilityNameResolver.LOCAL));
        }

        ResourceDescriptor descriptor = new ResourceDescriptor(this.getResourceDescriptionResolver())
                .addCapabilities(capabilities)
                .addCapabilities(localCapabilities)
                ;
        ResourceServiceHandler handler = new InfinispanSubsystemServiceHandler();
        new DeploymentChainContributingResourceRegistrar(descriptor, handler, this).register(registration);

        new CacheContainerResourceDefinition().register(registration);
        new RemoteCacheContainerResourceDefinition().register(registration);
    }

    @Override
    public void accept(DeploymentProcessorTarget target) {
        target.addDeploymentProcessor(InfinispanExtension.SUBSYSTEM_NAME, Phase.DEPENDENCIES, Phase.DEPENDENCIES_CLUSTERING, new ClusteringDependencyProcessor());
    }
}
