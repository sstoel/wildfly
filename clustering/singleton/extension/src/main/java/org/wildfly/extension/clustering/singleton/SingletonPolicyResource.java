/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.clustering.singleton;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.as.clustering.controller.ChildResourceProvider;
import org.jboss.as.clustering.controller.ComplexResource;
import org.jboss.as.clustering.controller.SimpleChildResourceProvider;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.server.deployment.Services;
import org.jboss.msc.service.ServiceName;
import org.wildfly.clustering.Registrar;
import org.wildfly.clustering.Registration;

/**
 * Custom resource that additionally reports runtime singleton deployments and services.
 * @author Paul Ferraro
 */
public class SingletonPolicyResource extends ComplexResource implements Registrar<ServiceName> {

    private static final String DEPLOYMENT_CHILD_TYPE = SingletonDeploymentResourceDefinition.WILDCARD_PATH.getKey();
    private static final String SERVICE_CHILD_TYPE = SingletonServiceResourceDefinition.WILDCARD_PATH.getKey();

    private static Map<String, ChildResourceProvider> createProviders() {
        Map<String, ChildResourceProvider> providers = new HashMap<>();
        providers.put(DEPLOYMENT_CHILD_TYPE, new SimpleChildResourceProvider(ConcurrentHashMap.newKeySet()));
        providers.put(SERVICE_CHILD_TYPE, new SimpleChildResourceProvider(ConcurrentHashMap.newKeySet()));
        return Collections.unmodifiableMap(providers);
    }

    public SingletonPolicyResource(Resource resource) {
        this(resource, createProviders());
    }

    private SingletonPolicyResource(Resource resource, Map<String, ChildResourceProvider> providers) {
        super(resource, providers, SingletonPolicyResource::new);
    }

    @Override
    public Registration register(ServiceName service) {
        boolean deployment = Services.JBOSS_DEPLOYMENT.isParentOf(service);
        ChildResourceProvider provider = this.apply(deployment ? DEPLOYMENT_CHILD_TYPE : SERVICE_CHILD_TYPE);
        String name = (deployment ? SingletonDeploymentResourceDefinition.pathElement(service) : SingletonServiceResourceDefinition.pathElement(service)).getValue();
        provider.getChildren().add(name);
        return new Registration() {
            @Override
            public void close() {
                provider.getChildren().remove(name);
            }
        };
    }
}
