/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.clustering.web.routing.infinispan;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.Configuration;
import org.jboss.as.controller.RequirementServiceTarget;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.msc.service.ServiceController;
import org.wildfly.clustering.cache.Key;
import org.wildfly.clustering.infinispan.service.InfinispanServiceDescriptor;
import org.wildfly.clustering.server.deployment.DeploymentConfiguration;
import org.wildfly.clustering.server.infinispan.CacheContainerGroupMember;
import org.wildfly.clustering.server.infinispan.affinity.NaryGroupMemberAffinity;
import org.wildfly.clustering.server.infinispan.registry.CacheContainerRegistry;
import org.wildfly.clustering.server.service.BinaryServiceConfiguration;
import org.wildfly.clustering.server.service.ClusteringServiceDescriptor;
import org.wildfly.clustering.session.cache.affinity.NarySessionAffinity;
import org.wildfly.clustering.session.cache.affinity.NarySessionAffinityConfiguration;
import org.wildfly.clustering.session.cache.affinity.SessionAffinityRegistryGroupMemberMapper;
import org.wildfly.clustering.web.service.routing.RouteLocatorProvider;
import org.wildfly.extension.clustering.web.routing.LocalRouteLocatorProvider;
import org.wildfly.subsystem.service.ServiceDependency;
import org.wildfly.subsystem.service.ServiceInstaller;

/**
 * Factory for creating a service configurator for a ranked route locator.
 * @author Paul Ferraro
 */
public class RankedRouteLocatorProvider extends LocalRouteLocatorProvider {

    private final NarySessionAffinityConfiguration config;

    public RankedRouteLocatorProvider(NarySessionAffinityConfiguration config) {
        this.config = config;
    }

    @Override
    public ServiceInstaller getServiceInstaller(DeploymentPhaseContext context, BinaryServiceConfiguration configuration, DeploymentConfiguration deployment) {
        ServiceDependency<Configuration> cacheConfiguration = configuration.getServiceDependency(InfinispanServiceDescriptor.CACHE_CONFIGURATION);
        return ServiceInstaller.builder(new ServiceInstaller() {
            @Override
            public ServiceController<?> install(RequirementServiceTarget target) {
                // Fallback to local routing if cache is local
                if (!cacheConfiguration.get().clustering().cacheMode().isClustered()) {
                    return RankedRouteLocatorProvider.super.getServiceInstaller(context, configuration, deployment).install(target);
                }
                ServiceDependency<Cache<Key<String>, ?>> cache = configuration.withChildName(deployment.getDeploymentName()).getServiceDependency(InfinispanServiceDescriptor.CACHE).map(Cache.class::cast);
                ServiceDependency<CacheContainerRegistry<String, Void>> registry = configuration.withChildName(deployment.getServerName()).getServiceDependency(ClusteringServiceDescriptor.REGISTRY).map(CacheContainerRegistry.class::cast);

                Supplier<UnaryOperator<String>> factory = new Supplier<>() {
                    @Override
                    public UnaryOperator<String> get() {
                        Function<String, List<CacheContainerGroupMember>> affinity = new NaryGroupMemberAffinity<>(cache.get(), registry.get().getGroup());
                        return new NarySessionAffinity<>(affinity, new SessionAffinityRegistryGroupMemberMapper<>(registry.get()), RankedRouteLocatorProvider.this.getNarySessionAffinityConfiguration());
                    }
                };
                return RouteLocatorProvider.builder(factory, deployment)
                        .requires(List.of(cache, registry))
                        .build()
                        .install(target);
            }
        }, context.getDeploymentUnit().getAttachment(Attachments.CAPABILITY_SERVICE_SUPPORT)).requires(cacheConfiguration).build();
    }

    public NarySessionAffinityConfiguration getNarySessionAffinityConfiguration() {
        return this.config;
    }
}
