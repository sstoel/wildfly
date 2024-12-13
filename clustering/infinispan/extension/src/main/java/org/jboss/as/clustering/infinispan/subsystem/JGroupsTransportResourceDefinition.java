/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.clustering.infinispan.subsystem;

import static org.jboss.as.clustering.infinispan.subsystem.JGroupsTransportResourceDefinition.Attribute.CHANNEL;
import static org.jboss.as.clustering.infinispan.subsystem.JGroupsTransportResourceDefinition.Attribute.LOCK_TIMEOUT;

import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.configuration.global.TransportConfiguration;
import org.infinispan.configuration.global.TransportConfigurationBuilder;
import org.infinispan.remoting.transport.jgroups.JGroupsTransport;
import org.jboss.as.clustering.controller.ResourceDescriptor;
import org.jboss.as.clustering.infinispan.transport.ChannelConfigurator;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.capability.UnaryCapabilityNameResolver;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jgroups.JChannel;
import org.wildfly.clustering.jgroups.spi.ChannelFactory;
import org.wildfly.clustering.jgroups.spi.ForkChannelFactory;
import org.wildfly.clustering.jgroups.spi.ForkStackConfiguration;
import org.wildfly.clustering.jgroups.spi.ProtocolStackConfiguration;
import org.wildfly.clustering.server.service.CacheContainerServiceInstallerProvider;
import org.wildfly.clustering.server.service.ProvidedBiServiceInstallerProvider;
import org.wildfly.clustering.server.util.MapEntry;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;
import org.wildfly.subsystem.resource.capability.CapabilityReferenceRecorder;
import org.wildfly.subsystem.resource.capability.ResourceCapabilityReferenceRecorder;
import org.wildfly.subsystem.service.ResourceServiceInstaller;
import org.wildfly.subsystem.service.ServiceDependency;
import org.wildfly.subsystem.service.capability.CapabilityServiceInstaller;

/**
 * Resource description for the addressable resource and its alias
 *
 * /subsystem=infinispan/cache-container=X/transport=jgroups
 *
 * @author Richard Achmatowicz (c) 2011 Red Hat Inc.
 */
public class JGroupsTransportResourceDefinition extends TransportResourceDefinition {

    static final PathElement PATH = pathElement("jgroups");

    static final UnaryServiceDescriptor<JChannel> TRANSPORT_CHANNEL = UnaryServiceDescriptor.of("org.wildfly.clustering.infinispan.cache-container.transport.channel", JChannel.class);

    private static final RuntimeCapability<Void> CAPABILITY = RuntimeCapability.Builder.of(TRANSPORT_CHANNEL).setDynamicNameMapper(UnaryCapabilityNameResolver.PARENT).build();

    enum Attribute implements org.jboss.as.clustering.controller.Attribute, UnaryOperator<SimpleAttributeDefinitionBuilder> {
        CHANNEL("channel", ModelType.STRING, null) {
            @Override
            public SimpleAttributeDefinitionBuilder apply(SimpleAttributeDefinitionBuilder builder) {
                return builder.setAllowExpression(false)
                        .setCapabilityReference(CapabilityReferenceRecorder.builder(CAPABILITY, ChannelFactory.SERVICE_DESCRIPTOR).build())
                        ;
            }
        },
        LOCK_TIMEOUT("lock-timeout", ModelType.LONG, new ModelNode(TimeUnit.MINUTES.toMillis(4))) {
            @Override
            public SimpleAttributeDefinitionBuilder apply(SimpleAttributeDefinitionBuilder builder) {
                return builder.setMeasurementUnit(MeasurementUnit.MILLISECONDS);
            }
        },
        ;
        private final AttributeDefinition definition;

        Attribute(String name, ModelType type, ModelNode defaultValue) {
            this.definition = this.apply(new SimpleAttributeDefinitionBuilder(name, type)
                    .setAllowExpression(true)
                    .setRequired(false)
                    .setDefaultValue(defaultValue)
                    .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES))
                    .build();
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

    static class ResourceDescriptorConfigurator implements UnaryOperator<ResourceDescriptor> {
        @Override
        public ResourceDescriptor apply(ResourceDescriptor descriptor) {
            return descriptor.addAttributes(Attribute.class)
                    .addCapabilities(List.of(CAPABILITY))
                    .addResourceCapabilityReference(ResourceCapabilityReferenceRecorder.builder(CAPABILITY, ChannelFactory.DEFAULT_SERVICE_DESCRIPTOR).build())
                    ;
        }
    }

    JGroupsTransportResourceDefinition() {
        super(PATH, new ResourceDescriptorConfigurator());
    }

    @Override
    public ResourceServiceInstaller configure(OperationContext context, ModelNode model) throws OperationFailedException {
        PathAddress address = context.getCurrentAddress();
        PathAddress containerAddress = address.getParent();
        String containerName = containerAddress.getLastElement().getValue();

        long lockTimeout = LOCK_TIMEOUT.resolveModelAttribute(context, model).asLong();
        String channel = CHANNEL.resolveModelAttribute(context, model).asStringOrNull();

        List<ResourceServiceInstaller> installers = new LinkedList<>();

        Function<ForkChannelFactory, TransportConfiguration> factory = new Function<>() {
            @Override
            public TransportConfiguration apply(ForkChannelFactory channelFactory) {
                Properties properties = new Properties();
                properties.put(JGroupsTransport.CHANNEL_CONFIGURATOR, new ChannelConfigurator(channelFactory, containerName));
                ProtocolStackConfiguration stack = channelFactory.getProtocolStackConfiguration();
                org.wildfly.clustering.jgroups.spi.TransportConfiguration.Topology topology = stack.getTransport().getTopology();
                JChannel channel = channelFactory.getForkStackConfiguration().getChannel();
                TransportConfigurationBuilder builder = new GlobalConfigurationBuilder().transport()
                        .clusterName(channel.getClusterName())
                        .distributedSyncTimeout(lockTimeout)
                        .nodeName(channel.getName())
                        .transport(new JGroupsTransport())
                        .withProperties(properties)
                        ;
                if (topology != null) {
                    builder.siteId(topology.getSite()).rackId(topology.getRack()).machineId(topology.getMachine());
                }
                return builder.create();
            }
        };
        installers.add(CapabilityServiceInstaller.builder(TransportResourceDefinition.CAPABILITY, ServiceDependency.on(ChannelFactory.SERVICE_DESCRIPTOR, channel).map(ForkChannelFactory.class::cast).map(factory)).build());
        installers.add(CapabilityServiceInstaller.builder(CAPABILITY, ServiceDependency.on(ChannelFactory.SERVICE_DESCRIPTOR, channel).map(ForkChannelFactory.class::cast).map(ForkChannelFactory::getForkStackConfiguration).map(ForkStackConfiguration::getChannel)).build());

        new ProvidedBiServiceInstallerProvider<>(CacheContainerServiceInstallerProvider.class, CacheContainerServiceInstallerProvider.class.getClassLoader()).apply(context.getCapabilityServiceSupport(), MapEntry.of(containerName, channel)).forEach(installers::add);

        return ResourceServiceInstaller.combine(installers);
    }
}
