/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.undertow.deployment;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Function;

import org.jboss.as.clustering.controller.CapabilityServiceConfigurator;
import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.web.common.WarMetaData;
import org.jboss.as.web.session.SharedSessionManagerConfig;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.metadata.web.spec.SessionConfigMetaData;
import org.wildfly.clustering.web.container.SessionManagementProvider;
import org.wildfly.clustering.web.container.SessionManagerFactoryConfiguration;
import org.wildfly.extension.undertow.ServletContainerService;
import org.wildfly.extension.undertow.logging.UndertowLogger;
import org.wildfly.extension.undertow.session.NonDistributableSessionManagementProvider;
import org.wildfly.extension.undertow.session.SessionManagementProviderFactory;

import io.undertow.server.session.InMemorySessionManager;
import io.undertow.servlet.api.SessionManagerFactory;

/**
 * @author Stuart Douglas
 */
public class SharedSessionManagerDeploymentProcessor implements DeploymentUnitProcessor, Function<SessionManagerFactoryConfiguration, SessionManagerFactory> {
    private final String defaultServerName;
    private final SessionManagementProviderFactory sessionManagementProviderFactory;
    private final SessionManagementProvider nonDistributableSessionManagementProvider;

    public SharedSessionManagerDeploymentProcessor(String defaultServerName) {
        this.defaultServerName = defaultServerName;
        Iterator<SessionManagementProviderFactory> factories = ServiceLoader.load(SessionManagementProviderFactory.class, SessionManagementProviderFactory.class.getClassLoader()).iterator();
        this.sessionManagementProviderFactory = factories.hasNext() ? factories.next() : null;
        this.nonDistributableSessionManagementProvider = new NonDistributableSessionManagementProvider(this);
    }

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        SharedSessionManagerConfig sharedConfig = deploymentUnit.getAttachment(SharedSessionManagerConfig.ATTACHMENT_KEY);
        if (sharedConfig == null) return;

        String deploymentName = (deploymentUnit.getParent() == null) ? deploymentUnit.getName() : String.join(".", deploymentUnit.getParent().getName(), deploymentUnit.getName());
        WarMetaData warMetaData = deploymentUnit.getAttachment(WarMetaData.ATTACHMENT_KEY);
        String serverName = Optional.ofNullable(warMetaData).map(metaData -> metaData.getMergedJBossWebMetaData().getServerInstanceName())
                .orElse(Optional.ofNullable(DefaultDeploymentMappingProvider.instance().getMapping(deploymentName)).map(Map.Entry::getKey).orElse(this.defaultServerName));
        SessionConfigMetaData sessionConfig = sharedConfig.getSessionConfig();
        ServletContainerService servletContainer = deploymentUnit.getAttachment(UndertowAttachments.SERVLET_CONTAINER_SERVICE);
        Integer defaultSessionTimeout = ((sessionConfig != null) && sessionConfig.getSessionTimeoutSet()) ? sessionConfig.getSessionTimeout() : (servletContainer != null) ? servletContainer.getDefaultSessionTimeout() : Integer.valueOf(30);

        CapabilityServiceSupport support = deploymentUnit.getAttachment(Attachments.CAPABILITY_SERVICE_SUPPORT);
        ServiceTarget target = phaseContext.getServiceTarget();
        ServiceName deploymentServiceName = deploymentUnit.getServiceName();

        ServiceName managerServiceName = deploymentServiceName.append(SharedSessionManagerConfig.SHARED_SESSION_MANAGER_SERVICE_NAME);
        ServiceName affinityServiceName = deploymentServiceName.append(SharedSessionManagerConfig.SHARED_SESSION_AFFINITY_SERVICE_NAME);

        SessionManagementProvider provider = this.getDistributableWebDeploymentProvider(deploymentUnit, sharedConfig);
        SessionManagerFactoryConfiguration configuration = new SessionManagerFactoryConfiguration() {
            @Override
            public String getServerName() {
                return serverName;
            }

            @Override
            public String getDeploymentName() {
                return deploymentName;
            }

            @Override
            public Integer getMaxActiveSessions() {
                return sharedConfig.getMaxActiveSessions();
            }

            @Override
            public DeploymentUnit getDeploymentUnit() {
                return deploymentUnit;
            }

            @Override
            public Duration getDefaultSessionTimeout() {
                return Duration.ofMinutes(defaultSessionTimeout);
            }
        };
        for (CapabilityServiceConfigurator configurator : provider.getSessionManagerFactoryServiceConfigurators(managerServiceName, configuration)) {
            configurator.configure(support).build(target).install();
        }
        for (CapabilityServiceConfigurator configurator : provider.getSessionAffinityServiceConfigurators(affinityServiceName, configuration)) {
            configurator.configure(support).build(target).install();
        }
    }

    @SuppressWarnings("deprecation")
    private SessionManagementProvider getDistributableWebDeploymentProvider(DeploymentUnit unit, SharedSessionManagerConfig config) {
        if (config.isDistributable()) {
            if (this.sessionManagementProviderFactory != null) {
                return this.sessionManagementProviderFactory.createSessionManagementProvider(unit, config.getReplicationConfig());
            }
            // Fallback to non-distributable session manager if server does not support clustering
            UndertowLogger.ROOT_LOGGER.clusteringNotSupported();
        }
        return this.nonDistributableSessionManagementProvider;
    }

    @Override
    public SessionManagerFactory apply(SessionManagerFactoryConfiguration configuration) {
        String deploymentName = configuration.getDeploymentName();
        Integer maxActiveSessions = configuration.getMaxActiveSessions();
        InMemorySessionManager manager = (maxActiveSessions != null) ? new InMemorySessionManager(deploymentName, maxActiveSessions.intValue()) : new InMemorySessionManager(deploymentName);
        manager.setDefaultSessionTimeout((int) configuration.getDefaultSessionTimeout().getSeconds());
        return new ImmediateSessionManagerFactory(manager);
    }
}
