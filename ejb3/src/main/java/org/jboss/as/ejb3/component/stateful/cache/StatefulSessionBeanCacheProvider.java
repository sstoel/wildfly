/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ejb3.component.stateful.cache;

import org.jboss.as.ee.component.ComponentConfiguration;
import org.jboss.as.ee.component.EEModuleConfiguration;
import org.jboss.as.ejb3.component.stateful.StatefulComponentDescription;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.wildfly.service.descriptor.NullaryServiceDescriptor;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;
import org.wildfly.subsystem.service.ServiceInstaller;

/**
 * Provides configurators for services to install a stateful session bean cache factory.
 * @author Paul Ferraro
 */
public interface StatefulSessionBeanCacheProvider {
    NullaryServiceDescriptor<StatefulSessionBeanCacheProvider> PASSIVATION_DISABLED_SERVICE_DESCRIPTOR = NullaryServiceDescriptor.of("org.wildfly.ejb.stateful.passation-disabled-cache", StatefulSessionBeanCacheProvider.class);
    NullaryServiceDescriptor<StatefulSessionBeanCacheProvider> DEFAULT_SERVICE_DESCRIPTOR = NullaryServiceDescriptor.of("org.wildfly.ejb.stateful.default-cache", StatefulSessionBeanCacheProvider.class);
    UnaryServiceDescriptor<StatefulSessionBeanCacheProvider> SERVICE_DESCRIPTOR = UnaryServiceDescriptor.of("org.wildfly.ejb.stateful.cache", DEFAULT_SERVICE_DESCRIPTOR);

    /**
     * Returns configurators for services to be installed for the specified deployment.
     * @param unit a deployment unit
     * @return a collection of service configurators
     */
    Iterable<ServiceInstaller> getDeploymentServiceInstallers(DeploymentUnit unit, EEModuleConfiguration moduleConfiguration);

    /**
     * Returns a configurator for a service supplying a cache factory.
     * @param unit the deployment unit containing this EJB component.
     * @param description the EJB component description
     * @param configuration the component configuration
     * @return a service configurator
     */
    Iterable<ServiceInstaller> getStatefulBeanCacheFactoryServiceInstallers(DeploymentUnit unit, StatefulComponentDescription description, ComponentConfiguration configuration);

    /**
     * Indicates whether or not cache factories provides by this object can support passivation.
     * @return true, if passivation is supported, false otherwise.
     */
    boolean supportsPassivation();
}
