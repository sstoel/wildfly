/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.messaging.activemq.jms;

import static org.wildfly.extension.messaging.activemq.Capabilities.ELYTRON_SSL_CONTEXT_CAPABILITY;
import static org.wildfly.extension.messaging.activemq.Capabilities.OUTBOUND_SOCKET_BINDING_CAPABILITY;
import static org.wildfly.extension.messaging.activemq.Capabilities.SOCKET_BINDING_CAPABILITY;
import static org.wildfly.extension.messaging.activemq.CommonAttributes.HA;
import static org.wildfly.extension.messaging.activemq.CommonAttributes.JGROUPS_CLUSTER;
import static org.wildfly.extension.messaging.activemq.CommonAttributes.JGROUPS_DISCOVERY_GROUP;
import static org.wildfly.extension.messaging.activemq.jms.ConnectionFactoryAttributes.Common.DESERIALIZATION_ALLOWLIST;
import static org.wildfly.extension.messaging.activemq.jms.ConnectionFactoryAttributes.Common.DESERIALIZATION_BLACKLIST;
import static org.wildfly.extension.messaging.activemq.jms.ConnectionFactoryAttributes.Common.DESERIALIZATION_BLOCKLIST;
import static org.wildfly.extension.messaging.activemq.jms.ConnectionFactoryAttributes.Common.DESERIALIZATION_WHITELIST;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;

import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.network.OutboundSocketBinding;
import org.jboss.as.network.SocketBinding;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.wildfly.extension.messaging.activemq.BinderServiceUtil;
import org.wildfly.extension.messaging.activemq.CommonAttributes;
import org.wildfly.extension.messaging.activemq.DiscoveryGroupDefinition;
import org.wildfly.extension.messaging.activemq.GroupBindingService;
import org.wildfly.extension.messaging.activemq.JGroupsDiscoveryGroupDefinition;
import org.wildfly.extension.messaging.activemq.MessagingServices;
import org.wildfly.extension.messaging.activemq.TransportConfigOperationHandlers;
import org.wildfly.extension.messaging.activemq.broadcast.BroadcastCommandDispatcherFactory;
import org.wildfly.extension.messaging.activemq.jms.ConnectionFactoryAttributes.Common;
import org.wildfly.extension.messaging.activemq.logging.MessagingLogger;

import static org.wildfly.extension.messaging.activemq.jms.ConnectionFactoryAttributes.External.ENABLE_AMQ1_PREFIX;

import javax.net.ssl.SSLContext;
import org.apache.activemq.artemis.jms.server.config.ConnectionFactoryConfiguration;
import org.jboss.as.controller.AttributeDefinition;

/**
 * Update adding a connection factory to the subsystem. The
 * runtime action will create the {@link ExternalConnectionFactoryService}.
 *
 * @author Emmanuel Hugonnet (c) 2018 Red Hat, inc.
 */
public class ExternalConnectionFactoryAdd extends AbstractAddStepHandler {

    public static final ExternalConnectionFactoryAdd INSTANCE = new ExternalConnectionFactoryAdd();

    private ExternalConnectionFactoryAdd() {
        super(ExternalConnectionFactoryDefinition.ATTRIBUTES);
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        final String name = context.getCurrentAddressValue();
        final ServiceName serviceName = ExternalConnectionFactoryDefinition.CAPABILITY.getCapabilityServiceName(context.getCurrentAddress());
        boolean ha = HA.resolveModelAttribute(context, model).asBoolean();
        boolean enable1Prefixes = ENABLE_AMQ1_PREFIX.resolveModelAttribute(context, model).asBoolean();
        final ModelNode discoveryGroupName = Common.DISCOVERY_GROUP.resolveModelAttribute(context, model);
        final ConnectionFactoryConfiguration config = ConnectionFactoryAdd.createConfiguration(context, name, model);
        JMSFactoryType jmsFactoryType = ConnectionFactoryType.valueOf(ConnectionFactoryAttributes.Regular.FACTORY_TYPE.resolveModelAttribute(context, model).asString()).getType();
        List<String> connectorNames = Common.CONNECTORS.unwrap(context, model);
        ServiceBuilder<?> builder = context.getServiceTarget()
                .addService(serviceName)
                .addAliases(JMSServices.getConnectionFactoryBaseServiceName(MessagingServices.getActiveMQServiceName()).append(name));
        ExternalConnectionFactoryService service;
        if (discoveryGroupName.isDefined()) {
            // mapping between the {discovery}-groups and the cluster names they use
            Map<String, String> clusterNames = new HashMap<>();
            Map<String, Supplier<SocketBinding>> groupBindings = new HashMap<>();
            // mapping between the {discovery}-groups and the command dispatcher factory they use
            Map<String, Supplier<BroadcastCommandDispatcherFactory>> commandDispatcherFactories = new HashMap<>();
            final String dgname = discoveryGroupName.asString();
            final String key = "discovery" + dgname;
            ModelNode discoveryGroupModel;
            try {
                discoveryGroupModel = context.readResourceFromRoot(context.getCurrentAddress().getParent().append(JGROUPS_DISCOVERY_GROUP, dgname)).getModel();
            } catch (Resource.NoSuchResourceException ex) {
                discoveryGroupModel = new ModelNode();
            }
            if (discoveryGroupModel.hasDefined(JGROUPS_CLUSTER.getName())) {
                ModelNode channel = JGroupsDiscoveryGroupDefinition.JGROUPS_CHANNEL.resolveModelAttribute(context, discoveryGroupModel);
                ServiceName commandDispatcherFactoryServiceName = MessagingServices.getBroadcastCommandDispatcherFactoryServiceName(channel.asStringOrNull());
                Supplier<BroadcastCommandDispatcherFactory> commandDispatcherFactorySupplier = builder.requires(commandDispatcherFactoryServiceName);
                commandDispatcherFactories.put(key, commandDispatcherFactorySupplier);
                String clusterName = JGROUPS_CLUSTER.resolveModelAttribute(context, discoveryGroupModel).asString();
                clusterNames.put(key, clusterName);
            } else {
                final ServiceName groupBinding = GroupBindingService.getDiscoveryBaseServiceName(MessagingServices.getActiveMQServiceName()).append(dgname);
                Supplier<SocketBinding> groupBindingSupplier = builder.requires(groupBinding);
                groupBindings.put(key, groupBindingSupplier);
            }
            service = new ExternalConnectionFactoryService(getDiscoveryGroup(context, dgname), commandDispatcherFactories, groupBindings, clusterNames, jmsFactoryType, ha, enable1Prefixes, config);
        } else {
            Map<String, Supplier<SocketBinding>> socketBindings = new HashMap<>();
            Map<String, Supplier<OutboundSocketBinding>> outboundSocketBindings = new HashMap<>();
            Set<String> connectorsSocketBindings = new HashSet<>();
            final Set<String> sslContextNames = new HashSet<>();
            TransportConfiguration[] transportConfigurations = TransportConfigOperationHandlers.processConnectors(context, connectorNames, connectorsSocketBindings, sslContextNames);
            Map<String, Boolean> outbounds = TransportConfigOperationHandlers.listOutBoundSocketBinding(context, connectorsSocketBindings);
            for (final String connectorSocketBinding : connectorsSocketBindings) {
                // find whether the connectorSocketBinding references a SocketBinding or an OutboundSocketBinding
                if (outbounds.get(connectorSocketBinding)) {
                    final ServiceName outboundSocketName = OUTBOUND_SOCKET_BINDING_CAPABILITY.getCapabilityServiceName(connectorSocketBinding);
                    Supplier<OutboundSocketBinding> outboundSupplier = builder.requires(outboundSocketName);
                    outboundSocketBindings.put(connectorSocketBinding, outboundSupplier);
                } else {
                    final ServiceName socketName = SOCKET_BINDING_CAPABILITY.getCapabilityServiceName(connectorSocketBinding);
                    Supplier<SocketBinding> socketBindingsSupplier = builder.requires(socketName);
                    socketBindings.put(connectorSocketBinding, socketBindingsSupplier);
                }
            }
            Map<String, Supplier<SSLContext>> sslContexts = new HashMap<>();
            for (final String entry : sslContextNames) {
                Supplier<SSLContext> sslContext = builder.requires(ELYTRON_SSL_CONTEXT_CAPABILITY.getCapabilityServiceName(entry));
                sslContexts.put(entry, sslContext);
            }
            service = new ExternalConnectionFactoryService(transportConfigurations, socketBindings, outboundSocketBindings, sslContexts, jmsFactoryType, ha, enable1Prefixes, config);
        }
        builder.setInstance(service);
        builder.install();
        for (String entry : Common.ENTRIES.unwrap(context, model)) {
            MessagingLogger.ROOT_LOGGER.debugf("Referencing %s with JNDI name %s", serviceName, entry);
            BinderServiceUtil.installBinderService(context.getServiceTarget(), entry, service, serviceName);
        }
    }

    static DiscoveryGroupConfiguration getDiscoveryGroup(final OperationContext context, final String name) throws OperationFailedException {
        Resource discoveryGroup;
        try {
            discoveryGroup = context.readResourceFromRoot(context.getCurrentAddress().getParent().append(PathElement.pathElement(CommonAttributes.JGROUPS_DISCOVERY_GROUP, name)), true);
        } catch (Resource.NoSuchResourceException ex) {
            discoveryGroup = context.readResourceFromRoot(context.getCurrentAddress().getParent().append(PathElement.pathElement(CommonAttributes.SOCKET_DISCOVERY_GROUP, name)), true);
        }
        if (discoveryGroup != null) {
            final long refreshTimeout = DiscoveryGroupDefinition.REFRESH_TIMEOUT.resolveModelAttribute(context, discoveryGroup.getModel()).asLong();
            final long initialWaitTimeout = DiscoveryGroupDefinition.INITIAL_WAIT_TIMEOUT.resolveModelAttribute(context, discoveryGroup.getModel()).asLong();

            return new DiscoveryGroupConfiguration()
                    .setName(name)
                    .setRefreshTimeout(refreshTimeout)
                    .setDiscoveryInitialWaitTimeout(initialWaitTimeout);
        }
        return null;
    }

    @Override
    protected void populateModel(final ModelNode operation, final ModelNode model) throws OperationFailedException {
        for (AttributeDefinition attr : attributes) {
            if (DESERIALIZATION_BLACKLIST.equals(attr)) {
                if (operation.hasDefined(DESERIALIZATION_BLACKLIST.getName())) {
                    DESERIALIZATION_BLOCKLIST.validateAndSet(operation, model);
                }
            } else if (DESERIALIZATION_WHITELIST.equals(attr)) {
                if (operation.hasDefined(DESERIALIZATION_WHITELIST.getName())) {
                    DESERIALIZATION_ALLOWLIST.validateAndSet(operation, model);
                }
            } else {
                attr.validateAndSet(operation, model);
            }
        }
    }
}
