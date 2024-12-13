/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.server.service;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ServiceNameFactory;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.wildfly.service.descriptor.BinaryServiceDescriptor;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;
import org.wildfly.subsystem.resource.ResourceModelResolver;
import org.wildfly.subsystem.service.ServiceDependency;

/**
 * Encapsulates a configuration tuple.
 * @author Paul Ferraro
 */
public interface BinaryServiceConfiguration {
    String getParentName();
    String getChildName();

    default BinaryServiceConfiguration withChildName(String childName) {
        return BinaryServiceConfiguration.of(this.getParentName(), childName);
    }

    default <T> ServiceDependency<T> getServiceDependency(BinaryServiceDescriptor<T> descriptor) {
        return ServiceDependency.on(descriptor, this.getParentName(), this.getChildName());
    }

    default <T> ServiceDependency<T> getServiceDependency(UnaryServiceDescriptor<T> descriptor) {
        return ServiceDependency.on(descriptor, this.getParentName());
    }

    default ServiceName resolveServiceName(BinaryServiceDescriptor<?> descriptor) {
        return ServiceNameFactory.resolveServiceName(descriptor, this.getParentName(), this.getChildName());
    }

    default ServiceName resolveServiceName(UnaryServiceDescriptor<?> descriptor) {
        return ServiceNameFactory.resolveServiceName(descriptor, this.getParentName());
    }

    static BinaryServiceConfiguration of(String parentName, String childName) {
        return new BinaryServiceConfiguration() {
            @Override
            public String getParentName() {
                return parentName;
            }

            @Override
            public String getChildName() {
                return childName;
            }
        };
    }

    static ResourceModelResolver<BinaryServiceConfiguration> resolver(AttributeDefinition containerAttribute, AttributeDefinition cacheAttribute) {
        return new ResourceModelResolver<>() {
            @Override
            public BinaryServiceConfiguration resolve(OperationContext context, ModelNode model) throws OperationFailedException {
                String containerName = containerAttribute.resolveModelAttribute(context, model).asString();
                String cacheName = cacheAttribute.resolveModelAttribute(context, model).asStringOrNull();
                return of(containerName, cacheName);
            }
        };
    }
}
