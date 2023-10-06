/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.undertow;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.wildfly.extension.undertow.Capabilities.CAPABILITY_HTTP_INVOKER;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import io.undertow.server.handlers.PathHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.ValueExpression;
import org.jboss.msc.service.ServiceController;
import org.wildfly.extension.undertow.filters.FilterDefinitions;
import org.wildfly.extension.undertow.handlers.HandlerDefinitions;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2012 Red Hat Inc.
 */
class UndertowRootDefinition extends PersistentResourceDefinition {

    static final PathElement PATH_ELEMENT = PathElement.pathElement(SUBSYSTEM, UndertowExtension.SUBSYSTEM_NAME);
    static final RuntimeCapability<Void> UNDERTOW_CAPABILITY = RuntimeCapability.Builder.of(Capabilities.CAPABILITY_UNDERTOW, false, UndertowService.class)
                        .build();

    static final RuntimeCapability<Void> HTTP_INVOKER_RUNTIME_CAPABILITY =
            RuntimeCapability.Builder.of(CAPABILITY_HTTP_INVOKER, false, PathHandler.class)
                    .build();

    protected static final SimpleAttributeDefinition DEFAULT_SERVLET_CONTAINER =
            new SimpleAttributeDefinitionBuilder(Constants.DEFAULT_SERVLET_CONTAINER, ModelType.STRING, true)
                    .setRestartAllServices()
                    .setDefaultValue(new ModelNode("default"))
                    .setCapabilityReference(UNDERTOW_CAPABILITY, Capabilities.CAPABILITY_SERVLET_CONTAINER)
                    .build();
    protected static final SimpleAttributeDefinition DEFAULT_SERVER =
            new SimpleAttributeDefinitionBuilder(Constants.DEFAULT_SERVER, ModelType.STRING, true)
                    .setRestartAllServices()
                    .setDefaultValue(new ModelNode("default-server"))
                    .setCapabilityReference(UNDERTOW_CAPABILITY, Capabilities.CAPABILITY_SERVER)
                    .build();

    protected static final SimpleAttributeDefinition DEFAULT_VIRTUAL_HOST =
                new SimpleAttributeDefinitionBuilder(Constants.DEFAULT_VIRTUAL_HOST, ModelType.STRING, true)
                        .setRestartAllServices()
                        .setDefaultValue(new ModelNode("default-host"))
                        .setCapabilityReference(UNDERTOW_CAPABILITY, Capabilities.CAPABILITY_HOST, DEFAULT_SERVER)
                        .build();

    protected static final SimpleAttributeDefinition INSTANCE_ID =
            new SimpleAttributeDefinitionBuilder(Constants.INSTANCE_ID, ModelType.STRING, true)
                    .setRestartAllServices()
                    .setAllowExpression(true)
                    .setDefaultValue(new ModelNode().set(new ValueExpression("${jboss.node.name}")))
                    .build();
    protected static final SimpleAttributeDefinition OBFUSCATE_SESSION_ROUTE =
            new SimpleAttributeDefinitionBuilder(Constants.OBFUSCATE_SESSION_ROUTE, ModelType.BOOLEAN, true)
                    .setRestartAllServices()
                    .setAllowExpression(true)
                    .setDefaultValue(ModelNode.FALSE)
                    .build();
    protected static final SimpleAttributeDefinition STATISTICS_ENABLED =
            new SimpleAttributeDefinitionBuilder(Constants.STATISTICS_ENABLED, ModelType.BOOLEAN, true)
                    .setRestartAllServices()
                    .setAllowExpression(true)
                    .setDefaultValue(ModelNode.FALSE)
                    .build();
    protected static final SimpleAttributeDefinition DEFAULT_SECURITY_DOMAIN =
            new SimpleAttributeDefinitionBuilder(Constants.DEFAULT_SECURITY_DOMAIN, ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setDefaultValue(new ModelNode("other"))
                    .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SECURITY_DOMAIN_REF)
                    .setRestartAllServices()
                    .build();


    static final Collection<AttributeDefinition> ATTRIBUTES = List.of(DEFAULT_VIRTUAL_HOST, DEFAULT_SERVLET_CONTAINER, DEFAULT_SERVER, INSTANCE_ID,
            OBFUSCATE_SESSION_ROUTE, STATISTICS_ENABLED, DEFAULT_SECURITY_DOMAIN);

    private final Set<String> knownApplicationSecurityDomains;

    UndertowRootDefinition() {
        this(new CopyOnWriteArraySet<>());
    }

    private UndertowRootDefinition(Set<String> knownApplicationSecurityDomains) {
        super(new SimpleResourceDefinition.Parameters(PATH_ELEMENT, UndertowExtension.getResolver())
                .setAddHandler(new UndertowSubsystemAdd(knownApplicationSecurityDomains::contains))
                .setRemoveHandler(ReloadRequiredRemoveStepHandler.INSTANCE)
                .addCapabilities(UNDERTOW_CAPABILITY, HTTP_INVOKER_RUNTIME_CAPABILITY)
        );
        this.knownApplicationSecurityDomains = knownApplicationSecurityDomains;
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return ATTRIBUTES;
    }

    @Override
    public List<? extends PersistentResourceDefinition> getChildren() {
        return List.of(
                new ByteBufferPoolDefinition(),
                new BufferCacheDefinition(),
                new ServerDefinition(),
                new ServletContainerDefinition(),
                new HandlerDefinitions(),
                new FilterDefinitions(),
                new ApplicationSecurityDomainDefinition(this.knownApplicationSecurityDomains));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        ReloadRequiredWriteAttributeHandler handler = new ReloadRequiredWriteAttributeHandler(getAttributes());
        for (AttributeDefinition attr : getAttributes()) {
            if (attr == STATISTICS_ENABLED) {
                resourceRegistration.registerReadWriteAttribute(attr, null, new AbstractWriteAttributeHandler<Void>(STATISTICS_ENABLED) {
                    @Override
                    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, HandbackHolder<Void> handbackHolder) throws OperationFailedException {
                        ServiceController<?> controller = context.getServiceRegistry(false).getService(UndertowService.UNDERTOW);
                        if (controller != null) {
                            UndertowService service = (UndertowService) controller.getService();
                            if (service != null) {
                                service.setStatisticsEnabled(resolvedValue.asBoolean());
                            }
                        }
                        return false;
                    }

                    @Override
                    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode valueToRevert, Void handback) throws OperationFailedException {
                        ServiceController<?> controller = context.getServiceRegistry(false).getService(UndertowService.UNDERTOW);
                        if (controller != null) {
                            UndertowService service = (UndertowService) controller.getService();
                            if (service != null) {
                                service.setStatisticsEnabled(valueToRestore.asBoolean());
                            }
                        }
                    }
                });
            } else {
                resourceRegistration.registerReadWriteAttribute(attr, null, handler);
            }
        }
    }
}
