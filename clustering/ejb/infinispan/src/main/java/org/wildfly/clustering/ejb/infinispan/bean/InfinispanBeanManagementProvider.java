/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.ejb.infinispan.bean;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.ExpirationConfiguration;
import org.infinispan.configuration.cache.StorageType;
import org.infinispan.eviction.EvictionStrategy;
import org.jboss.msc.service.ServiceName;
import org.wildfly.clustering.cache.CacheEntryCreator;
import org.wildfly.clustering.cache.CacheEntryMutatorFactory;
import org.wildfly.clustering.cache.CacheEntryRemover;
import org.wildfly.clustering.cache.CacheProperties;
import org.wildfly.clustering.cache.infinispan.embedded.EmbeddedCacheConfiguration;
import org.wildfly.clustering.cache.infinispan.embedded.container.DataContainerConfigurationBuilder;
import org.wildfly.clustering.ejb.DeploymentConfiguration;
import org.wildfly.clustering.ejb.bean.BeanConfiguration;
import org.wildfly.clustering.ejb.bean.BeanDeploymentConfiguration;
import org.wildfly.clustering.ejb.bean.BeanInstance;
import org.wildfly.clustering.ejb.bean.BeanManagementConfiguration;
import org.wildfly.clustering.ejb.bean.BeanManagementProvider;
import org.wildfly.clustering.ejb.bean.BeanManagerFactory;
import org.wildfly.clustering.ejb.bean.BeanPassivationConfiguration;
import org.wildfly.clustering.ejb.cache.bean.BeanGroupManager;
import org.wildfly.clustering.ejb.cache.bean.DefaultBeanGroupManager;
import org.wildfly.clustering.ejb.cache.bean.DefaultBeanGroupManagerConfiguration;
import org.wildfly.clustering.ejb.infinispan.logging.InfinispanEjbLogger;
import org.wildfly.clustering.infinispan.service.CacheServiceInstallerFactory;
import org.wildfly.clustering.infinispan.service.InfinispanServiceDescriptor;
import org.wildfly.clustering.infinispan.service.TemplateConfigurationServiceInstallerFactory;
import org.wildfly.clustering.marshalling.ByteBufferMarshalledValueFactory;
import org.wildfly.clustering.marshalling.ByteBufferMarshaller;
import org.wildfly.clustering.marshalling.MarshalledValue;
import org.wildfly.clustering.marshalling.MarshalledValueFactory;
import org.wildfly.clustering.server.Registration;
import org.wildfly.clustering.server.infinispan.dispatcher.CacheContainerCommandDispatcherFactory;
import org.wildfly.clustering.server.service.BinaryServiceConfiguration;
import org.wildfly.clustering.server.service.ClusteringServiceDescriptor;
import org.wildfly.common.function.Functions;
import org.wildfly.subsystem.service.ServiceDependency;
import org.wildfly.subsystem.service.ServiceInstaller;

/**
 * Builds an infinispan-based {@link BeanManagerFactory}.
 *
 * @author Paul Ferraro
 */
public class InfinispanBeanManagementProvider<K, V extends BeanInstance<K>> implements BeanManagementProvider {

    private final String name;
    private final BeanManagementConfiguration configuration;
    private final BinaryServiceConfiguration cacheConfiguration;

    public InfinispanBeanManagementProvider(String name, BeanManagementConfiguration configuration, BinaryServiceConfiguration cacheConfiguration) {
        this.name = name;
        this.configuration = configuration;
        this.cacheConfiguration = cacheConfiguration;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Iterable<ServiceInstaller> getDeploymentServiceInstallers(BeanDeploymentConfiguration deploymentConfiguration) {
        BinaryServiceConfiguration deploymentCacheConfiguration = this.cacheConfiguration.withChildName(deploymentConfiguration.getDeploymentName());

        // Ensure eviction and expiration are disabled
        Consumer<ConfigurationBuilder> configurator = builder -> {
            // Ensure expiration is not enabled on cache
            ExpirationConfiguration expiration = builder.expiration().create();
            if ((expiration.lifespan() >= 0) || (expiration.maxIdle() >= 0)) {
                builder.expiration().lifespan(-1).maxIdle(-1);
                InfinispanEjbLogger.ROOT_LOGGER.expirationDisabled(this.cacheConfiguration.getChildName());
            }

            OptionalInt size = this.configuration.getMaxActiveBeans();
            EvictionStrategy strategy = size.isPresent() ? EvictionStrategy.REMOVE : EvictionStrategy.MANUAL;
            builder.memory().storage(StorageType.HEAP).whenFull(strategy).maxCount(size.orElse(0));
            if (strategy.isEnabled()) {
                // Only evict bean group entries
                // We will cascade eviction to the associated beans
                builder.addModule(DataContainerConfigurationBuilder.class).evictable(InfinispanBeanGroupKey.class::isInstance);
            }
        };

        ServiceInstaller cacheConfigurationInstaller = new TemplateConfigurationServiceInstallerFactory(configurator).apply(this.cacheConfiguration, deploymentCacheConfiguration);
        ServiceInstaller cacheInstaller = CacheServiceInstallerFactory.INSTANCE.apply(deploymentCacheConfiguration);

        ByteBufferMarshaller marshaller = this.configuration.getMarshallerFactory().apply(deploymentConfiguration);

        ServiceDependency<Cache<?, ?>> cache = deploymentCacheConfiguration.getServiceDependency(InfinispanServiceDescriptor.CACHE);
        EmbeddedCacheConfiguration cacheConfiguration = new EmbeddedCacheConfiguration() {
            @SuppressWarnings("unchecked")
            @Override
            public <KK, VV> Cache<KK, VV> getCache() {
                return (Cache<KK, VV>) cache.get();
            }
        };
        Supplier<BeanGroupManager<K, V>> groupFactory = new Supplier<>() {
            @Override
            public BeanGroupManager<K, V> get() {
                InfinispanBeanGroupManager<K, V, ByteBufferMarshaller> groupManager = new InfinispanBeanGroupManager<>(cacheConfiguration);
                DefaultBeanGroupManagerConfiguration<K, V, ByteBufferMarshaller> groupManagerConfiguration = new DefaultBeanGroupManagerConfiguration<>() {
                    @Override
                    public CacheEntryCreator<K, MarshalledValue<Map<K, V>, ByteBufferMarshaller>, MarshalledValue<Map<K, V>, ByteBufferMarshaller>> getCreator() {
                        return groupManager;
                    }

                    @Override
                    public CacheEntryRemover<K> getRemover() {
                        return groupManager;
                    }

                    @Override
                    public CacheEntryMutatorFactory<K, MarshalledValue<Map<K, V>, ByteBufferMarshaller>> getMutatorFactory() {
                        return groupManager;
                    }

                    @Override
                    public CacheProperties getCacheProperties() {
                        return cacheConfiguration.getCacheProperties();
                    }

                    @Override
                    public MarshalledValueFactory<ByteBufferMarshaller> getMarshalledValueFactory() {
                        return new ByteBufferMarshalledValueFactory(marshaller);
                    }
                };
                return new DefaultBeanGroupManager<>(groupManagerConfiguration);
            }
        };
        ServiceName groupManagerServiceName = this.getGroupManagerServiceName(deploymentConfiguration);
        ServiceInstaller groupManagerInstaller = ServiceInstaller.builder(groupFactory)
                .provides(groupManagerServiceName)
                .requires(cache)
                .build();

        Supplier<Registration> groupListener = new Supplier<>() {
            @Override
            public Registration get() {
                return new InfinispanBeanGroupListener<>(cacheConfiguration, marshaller);
            }
        };
        ServiceInstaller groupListenerInstaller = ServiceInstaller.builder(groupListener)
                .onStop(Functions.closingConsumer())
                .requires(ServiceDependency.on(groupManagerServiceName))
                .asPassive()
                .build();

        return List.of(cacheConfigurationInstaller, cacheInstaller, groupManagerInstaller, groupListenerInstaller);
    }

    @Override
    public ServiceInstaller getBeanManagerFactoryServiceInstaller(ServiceName name, BeanConfiguration beanConfiguration) {
        BinaryServiceConfiguration deploymentCacheConfiguration = this.cacheConfiguration.withChildName(beanConfiguration.getDeploymentName());
        ServiceDependency<Cache<?, ?>> cache = deploymentCacheConfiguration.getServiceDependency(InfinispanServiceDescriptor.CACHE);
        ServiceDependency<CacheContainerCommandDispatcherFactory> dispatcherFactory = deploymentCacheConfiguration.getServiceDependency(ClusteringServiceDescriptor.COMMAND_DISPATCHER_FACTORY).map(CacheContainerCommandDispatcherFactory.class::cast);
        ServiceDependency<BeanGroupManager<K, V>> beanGroupManager = ServiceDependency.on(this.getGroupManagerServiceName(beanConfiguration));
        InfinispanBeanManagerFactoryConfiguration<K, V> configuration = new InfinispanBeanManagerFactoryConfiguration<>() {
            @Override
            public BeanConfiguration getBeanConfiguration() {
                return beanConfiguration;
            }

            @SuppressWarnings("unchecked")
            @Override
            public <KK, VV> Cache<KK, VV> getCache() {
                return (Cache<KK, VV>) cache.get();
            }

            @Override
            public BeanPassivationConfiguration getPassivationConfiguration() {
                return InfinispanBeanManagementProvider.this.configuration;
            }

            @Override
            public CacheContainerCommandDispatcherFactory getCommandDispatcherFactory() {
                return dispatcherFactory.get();
            }

            @Override
            public BeanGroupManager<K, V> getBeanGroupManager() {
                return beanGroupManager.get();
            }
        };
        return ServiceInstaller.builder(Functions.constantSupplier(new InfinispanBeanManagerFactory<>(configuration)))
                .provides(name)
                .requires(List.of(cache, dispatcherFactory, beanGroupManager))
                .build();
    }

    private ServiceName getGroupManagerServiceName(DeploymentConfiguration config) {
        return config.getDeploymentServiceName().append(this.name, "bean-group");
    }
}
