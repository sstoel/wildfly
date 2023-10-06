/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ejb3.deployment.processors;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.ee.metadata.EJBClientDescriptorMetaData;
import org.jboss.as.ee.structure.Attachments;
import org.jboss.as.ejb3.deployment.EjbDeploymentAttachmentKeys;
import org.jboss.as.ejb3.logging.EjbLogger;
import org.jboss.as.ejb3.remote.EJBClientContextService;
import org.jboss.as.ejb3.remote.LocalTransportProvider;
import org.jboss.as.ejb3.remote.RemotingProfileService;
import org.jboss.as.ejb3.subsystem.EJBClientConfiguratorService;
import org.jboss.as.network.OutboundConnection;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.ejb.client.ClusterNodeSelector;
import org.jboss.ejb.client.DeploymentNodeSelector;
import org.jboss.ejb.client.EJBClientCluster;
import org.jboss.ejb.client.EJBClientInterceptor;
import org.jboss.ejb.client.EJBTransportProvider;
import org.jboss.modules.Module;
import org.jboss.msc.inject.InjectionException;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.remoting3.RemotingOptions;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.MatchRule;
import org.xnio.Option;
import org.xnio.OptionMap;

/**
 * A deployment unit processor which processing only top level deployment units and checks for the presence of a
 * {@link Attachments#EJB_CLIENT_METADATA} key corresponding to {@link EJBClientDescriptorMetaData}, in the deployment unit.
 * <p/>
 * If a {@link EJBClientDescriptorMetaData} is available then this deployment unit processor creates and installs a
 * {@link EJBClientContextService}.
 *
 * TODO Elytron emulate old configuration using discovery, clustering
 *
 * @author Jaikiran Pai
 * @author <a href="mailto:tadamski@redhat.com">Tomasz Adamski</a>
 */
public class EJBClientDescriptorMetaDataProcessor implements DeploymentUnitProcessor {

    private static final String INTERNAL_REMOTING_PROFILE = "internal-remoting-profile";
    private static final String OUTBOUND_CONNECTION_CAPABILITY_NAME = "org.wildfly.remoting.outbound-connection";
    private static final String REMOTING_PROFILE_CAPABILITY_NAME = "org.wildfly.ejb3.remoting-profile";

    private final boolean appclient;

    public EJBClientDescriptorMetaDataProcessor(boolean appclient) {
        this.appclient = appclient;
    }

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        // we only process top level deployment units
        if (deploymentUnit.getParent() != null) {
            return;
        }
        final Module module = deploymentUnit.getAttachment(org.jboss.as.server.deployment.Attachments.MODULE);
        if (module == null) {
            return;
        }

        // support for using capabilities to resolve service names
        CapabilityServiceSupport capabilityServiceSupport = deploymentUnit.getAttachment(org.jboss.as.server.deployment.Attachments.CAPABILITY_SERVICE_SUPPORT);

        // check for EJB client interceptor configuration
        final List<EJBClientInterceptor> deploymentEjbClientInterceptors = getClassPathInterceptors(module.getClassLoader());
        List<EJBClientInterceptor> staticEjbClientInterceptors = deploymentUnit.getAttachment(org.jboss.as.ejb3.subsystem.Attachments.STATIC_EJB_CLIENT_INTERCEPTORS);

        List<EJBClientInterceptor> ejbClientInterceptors = new ArrayList<>();

        if(deploymentEjbClientInterceptors != null){
            ejbClientInterceptors.addAll(deploymentEjbClientInterceptors);
        }

        if(staticEjbClientInterceptors != null){
            ejbClientInterceptors.addAll(staticEjbClientInterceptors);
        }

        final boolean interceptorsDefined = ejbClientInterceptors != null && ! ejbClientInterceptors.isEmpty();

        final EJBClientDescriptorMetaData ejbClientDescriptorMetaData = deploymentUnit.getAttachment(Attachments.EJB_CLIENT_METADATA);

        // no explicit EJB client configuration in this deployment, so nothing to do
        if (ejbClientDescriptorMetaData == null && ! interceptorsDefined) {
            return;
        }
        // install the descriptor based EJB client context service
        final ServiceName ejbClientContextServiceName = EJBClientContextService.DEPLOYMENT_BASE_SERVICE_NAME.append(deploymentUnit.getName());
        final ServiceTarget serviceTarget = phaseContext.getServiceTarget();
        // create the service
        final EJBClientContextService service = new EJBClientContextService();
        // add the service
        final ServiceBuilder<EJBClientContextService> serviceBuilder = serviceTarget.addService(ejbClientContextServiceName, service);

        if (appclient) {
            serviceBuilder.addDependency(EJBClientContextService.APP_CLIENT_URI_SERVICE_NAME, URI.class, service.getAppClientUri());
            serviceBuilder.addDependency(EJBClientContextService.APP_CLIENT_EJB_PROPERTIES_SERVICE_NAME, String.class, service.getAppClientEjbProperties());
        }
        //default transport providers: remote from config, local from service, in "else" below.
        serviceBuilder.addDependency(EJBClientConfiguratorService.SERVICE_NAME, EJBClientConfiguratorService.class, service.getConfiguratorServiceInjector());

        if (ejbClientDescriptorMetaData != null) {
            // profile and remoting-ejb-receivers cannot be used together
            checkDescriptorConfiguration(ejbClientDescriptorMetaData);

            final Injector<RemotingProfileService> profileServiceInjector = new Injector<RemotingProfileService>() {
                final Injector<EJBTransportProvider> injector = service.getLocalProviderInjector();
                boolean injected = false;

                public void inject(final RemotingProfileService value) throws InjectionException {
                    final Supplier<EJBTransportProvider> transportSupplier = value.getLocalTransportProviderSupplier();
                    final EJBTransportProvider provider = transportSupplier != null ? transportSupplier.get() : null;
                    if (provider != null) {
                        injected = true;
                        injector.inject(provider);
                    }
                }

                public void uninject() {
                    if (injected) {
                        injected = false;
                        injector.uninject();
                    }
                }
            };
            final String profile = ejbClientDescriptorMetaData.getProfile();
            final ServiceName profileServiceName;
            if (profile != null) {
                // set up a service for the named remoting profile
                profileServiceName = capabilityServiceSupport.getCapabilityServiceName(REMOTING_PROFILE_CAPABILITY_NAME, profile);
                // why below?
                serviceBuilder.addDependency(profileServiceName, RemotingProfileService.class, profileServiceInjector);
                serviceBuilder.addDependency(profileServiceName, RemotingProfileService.class, service.getProfileServiceInjector());
            } else {
                // if descriptor defines list of ejb-receivers instead of profile then we create internal ProfileService for this
                // application which contains defined receivers
                profileServiceName = ejbClientContextServiceName.append(INTERNAL_REMOTING_PROFILE);
                final Map<String, RemotingProfileService.RemotingConnectionSpec> remotingConnectionMap = new HashMap<>();
                final List<RemotingProfileService.HttpConnectionSpec> httpConnections = new ArrayList<>();
                final ServiceBuilder<?> profileServiceBuilder = serviceTarget.addService(profileServiceName);
                final Consumer<RemotingProfileService> consumer = profileServiceBuilder.provides(profileServiceName);
                Supplier<EJBTransportProvider> localTransportProviderSupplier = null;
                if (ejbClientDescriptorMetaData.isLocalReceiverExcluded() != Boolean.TRUE) {
                    final Boolean passByValue = ejbClientDescriptorMetaData.isLocalReceiverPassByValue();
                    localTransportProviderSupplier = profileServiceBuilder.requires(passByValue == Boolean.FALSE ? LocalTransportProvider.BY_REFERENCE_SERVICE_NAME : LocalTransportProvider.BY_VALUE_SERVICE_NAME);
                }
                final Collection<EJBClientDescriptorMetaData.RemotingReceiverConfiguration> receiverConfigurations = ejbClientDescriptorMetaData.getRemotingReceiverConfigurations();

                for (EJBClientDescriptorMetaData.RemotingReceiverConfiguration receiverConfiguration : receiverConfigurations) {
                    final String connectionRef = receiverConfiguration.getOutboundConnectionRef();
                    final long connectTimeout = receiverConfiguration.getConnectionTimeout();
                    final Properties channelCreationOptions = receiverConfiguration.getChannelCreationOptions();
                    final OptionMap optionMap = getOptionMapFromProperties(channelCreationOptions, EJBClientDescriptorMetaDataProcessor.class.getClassLoader());
                    final ServiceName internalServiceName = capabilityServiceSupport.getCapabilityServiceName(OUTBOUND_CONNECTION_CAPABILITY_NAME, connectionRef);
                    final Supplier<OutboundConnection> supplier = profileServiceBuilder.requires(internalServiceName);
                    final RemotingProfileService.RemotingConnectionSpec connectionSpec = new RemotingProfileService.RemotingConnectionSpec(connectionRef, supplier, optionMap, connectTimeout);
                    remotingConnectionMap.put(connectionRef, connectionSpec);
                }
                for (EJBClientDescriptorMetaData.HttpConnectionConfiguration httpConfigurations : ejbClientDescriptorMetaData.getHttpConnectionConfigurations()) {
                    final String uri = httpConfigurations.getUri();
                    RemotingProfileService.HttpConnectionSpec httpConnectionSpec = new RemotingProfileService.HttpConnectionSpec(uri);
                    httpConnections.add(httpConnectionSpec);
                }
                final RemotingProfileService profileService = new RemotingProfileService(consumer, localTransportProviderSupplier, Collections.emptyList(), remotingConnectionMap, httpConnections);
                profileServiceBuilder.setInstance(profileService);
                profileServiceBuilder.install();

                serviceBuilder.addDependency(profileServiceName, RemotingProfileService.class, profileServiceInjector);
                serviceBuilder.addDependency(profileServiceName, RemotingProfileService.class, service.getProfileServiceInjector());
            }
            // these items are the same no matter how we were configured
            final String deploymentNodeSelectorClassName = ejbClientDescriptorMetaData.getDeploymentNodeSelector();
            if (deploymentNodeSelectorClassName != null) {
                final DeploymentNodeSelector deploymentNodeSelector;
                try {
                    deploymentNodeSelector = module.getClassLoader().loadClass(deploymentNodeSelectorClassName).asSubclass(DeploymentNodeSelector.class).getConstructor().newInstance();
                } catch (Exception e) {
                    throw EjbLogger.ROOT_LOGGER.failedToCreateDeploymentNodeSelector(e, deploymentNodeSelectorClassName);
                }
                service.setDeploymentNodeSelector(deploymentNodeSelector);
            }
            final long invocationTimeout = ejbClientDescriptorMetaData.getInvocationTimeout();
            service.setInvocationTimeout(invocationTimeout);

            final int defaultCompression = ejbClientDescriptorMetaData.getDefaultCompression();
            service.setDefaultCompression(defaultCompression);

            // clusters
            final Collection<EJBClientDescriptorMetaData.ClusterConfig> clusterConfigs = ejbClientDescriptorMetaData.getClusterConfigs();
            if (!clusterConfigs.isEmpty()) {
                final List<EJBClientCluster> clientClusters = new ArrayList<>(clusterConfigs.size());
                AuthenticationContext clustersAuthenticationContext = AuthenticationContext.empty();
                for (EJBClientDescriptorMetaData.ClusterConfig clusterConfig : clusterConfigs) {
                    MatchRule defaultRule = MatchRule.ALL.matchAbstractType("ejb", "jboss");
                    AuthenticationConfiguration defaultAuthenticationConfiguration = AuthenticationConfiguration.empty();
                    final EJBClientCluster.Builder clientClusterBuilder = new EJBClientCluster.Builder();

                    final String clusterName = clusterConfig.getClusterName();
                    clientClusterBuilder.setName(clusterName);
                    defaultRule = defaultRule.matchProtocol("cluster");
                    defaultRule = defaultRule.matchUrnName(clusterName);

                    final long maxAllowedConnectedNodes = clusterConfig.getMaxAllowedConnectedNodes();
                    clientClusterBuilder.setMaximumConnectedNodes(maxAllowedConnectedNodes);

                    final String clusterNodeSelectorClassName = clusterConfig.getNodeSelector();
                    if (clusterNodeSelectorClassName != null) {
                        final ClusterNodeSelector clusterNodeSelector;
                        try {
                            clusterNodeSelector = module.getClassLoader().loadClass(clusterNodeSelectorClassName).asSubclass(ClusterNodeSelector.class).getConstructor().newInstance();
                        } catch (Exception e) {
                            throw EjbLogger.ROOT_LOGGER.failureDuringLoadOfClusterNodeSelector(clusterNodeSelectorClassName, clusterName, e);
                        }
                        clientClusterBuilder.setClusterNodeSelector(clusterNodeSelector);
                    }

                    final Properties clusterChannelCreationOptions = clusterConfig.getChannelCreationOptions();
                    final OptionMap clusterChannelCreationOptionMap = getOptionMapFromProperties(clusterChannelCreationOptions, EJBClientDescriptorMetaDataProcessor.class.getClassLoader());
                    final Properties clusterConnectionOptions = clusterConfig.getConnectionOptions();
                    final OptionMap clusterConnectionOptionMap = getOptionMapFromProperties(clusterConnectionOptions, EJBClientDescriptorMetaDataProcessor.class.getClassLoader());
                    final long clusterConnectTimeout = clusterConfig.getConnectTimeout();
                    clientClusterBuilder.setConnectTimeoutMilliseconds(clusterConnectTimeout);

                    if (clusterConnectionOptionMap != null) {
                        RemotingOptions.mergeOptionsIntoAuthenticationConfiguration(clusterConnectionOptionMap, defaultAuthenticationConfiguration);
                    }
                    clustersAuthenticationContext = clustersAuthenticationContext.with(defaultRule, defaultAuthenticationConfiguration);

                    final Collection<EJBClientDescriptorMetaData.ClusterNodeConfig> clusterNodeConfigs = clusterConfig.getClusterNodeConfigs();
                    for (EJBClientDescriptorMetaData.ClusterNodeConfig clusterNodeConfig : clusterNodeConfigs) {
                        MatchRule nodeRule = MatchRule.ALL.matchAbstractType("ejb", "jboss");
                        AuthenticationConfiguration nodeAuthenticationConfiguration = AuthenticationConfiguration.empty();

                        final String nodeName = clusterNodeConfig.getNodeName();
                        nodeRule = nodeRule.matchProtocol("node");
                        nodeRule = nodeRule.matchUrnName(nodeName);

                        final Properties channelCreationOptions = clusterNodeConfig.getChannelCreationOptions();
                        final Properties connectionOptions = clusterNodeConfig.getConnectionOptions();
                        final OptionMap connectionOptionMap = getOptionMapFromProperties(connectionOptions, EJBClientDescriptorMetaDataProcessor.class.getClassLoader());
                        final long connectTimeout = clusterNodeConfig.getConnectTimeout();

                        if (connectionOptionMap != null) {
                            RemotingOptions.mergeOptionsIntoAuthenticationConfiguration(connectionOptionMap, nodeAuthenticationConfiguration);
                        }
                        clustersAuthenticationContext = clustersAuthenticationContext.with(0, nodeRule, nodeAuthenticationConfiguration);
                    }
                    final EJBClientCluster clientCluster = clientClusterBuilder.build();
                    clientClusters.add(clientCluster);
                }
                service.setClientClusters(clientClusters);
                service.setClustersAuthenticationContext(clustersAuthenticationContext);
            }
            deploymentUnit.putAttachment(EjbDeploymentAttachmentKeys.EJB_REMOTING_PROFILE_SERVICE_NAME, profileServiceName);
        } else {
            if(!appclient) {
                serviceBuilder.addDependency(LocalTransportProvider.DEFAULT_LOCAL_TRANSPORT_PROVIDER_SERVICE_NAME, EJBTransportProvider.class, service.getLocalProviderInjector());
            }
        }

        if (interceptorsDefined) {
            service.setClientInterceptors(ejbClientInterceptors);
        }

        // install the service
        serviceBuilder.install();
        EjbLogger.DEPLOYMENT_LOGGER.debugf("Deployment unit %s will use %s as the EJB client context service", deploymentUnit,
                ejbClientContextServiceName);

        // attach the service name of this EJB client context to the deployment unit
        phaseContext.addDeploymentDependency(ejbClientContextServiceName, EjbDeploymentAttachmentKeys.EJB_CLIENT_CONTEXT_SERVICE);
        deploymentUnit.putAttachment(EjbDeploymentAttachmentKeys.EJB_CLIENT_CONTEXT_SERVICE_NAME, ejbClientContextServiceName);
    }

    private void checkDescriptorConfiguration(final EJBClientDescriptorMetaData ejbClientDescriptorMetaData)
            throws DeploymentUnitProcessingException {
        final boolean profileDefined = ejbClientDescriptorMetaData.getProfile() != null;
        final boolean receiversDefined = (!ejbClientDescriptorMetaData.getRemotingReceiverConfigurations().isEmpty())
                || (ejbClientDescriptorMetaData.isLocalReceiverExcluded() != null)
                || (ejbClientDescriptorMetaData.isLocalReceiverPassByValue() != null);
        if (profileDefined && receiversDefined) {
            throw EjbLogger.ROOT_LOGGER.profileAndRemotingEjbReceiversUsedTogether();
        }
    }

    private OptionMap getOptionMapFromProperties(final Properties properties, final ClassLoader classLoader) {
        final OptionMap.Builder optionMapBuilder = OptionMap.builder();
        if (properties != null) for (final String propertyName : properties.stringPropertyNames()) {
            try {
                final Option<?> option = Option.fromString(propertyName, classLoader);
                optionMapBuilder.parse(option, properties.getProperty(propertyName), classLoader);
            } catch (IllegalArgumentException e) {
                EjbLogger.DEPLOYMENT_LOGGER.failedToCreateOptionForProperty(propertyName, e.getMessage());
            }
        }
        return optionMapBuilder.getMap();
    }

    private List<EJBClientInterceptor> getClassPathInterceptors(final ClassLoader classLoader) throws DeploymentUnitProcessingException {
        try {
            final Enumeration<URL> resources = classLoader.getResources("META-INF/services/org.jboss.ejb.client.EJBClientInterceptor");
            final ArrayList<EJBClientInterceptor> interceptors = new ArrayList<>();
            if (resources.hasMoreElements()) {
                do {
                    final URL url = resources.nextElement();
                    try (InputStream st = url.openStream();
                         InputStreamReader isr = new InputStreamReader(st, StandardCharsets.UTF_8);
                         BufferedReader r = new BufferedReader(isr)) {
                        String line;
                        while ((line = r.readLine()) != null) {
                            line = line.trim();
                            if (line.isEmpty() || line.charAt(0) == '#') {
                                continue;
                            }
                            try {
                                final EJBClientInterceptor interceptor = Class.forName(line, true, classLoader).asSubclass(EJBClientInterceptor.class).getConstructor().newInstance();
                                interceptors.add(interceptor);
                            } catch (Exception e) {
                                throw EjbLogger.ROOT_LOGGER.failedToCreateEJBClientInterceptor(e, line);
                            }
                        }
                    }
                } while (resources.hasMoreElements());
            }
            return interceptors;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }
}
