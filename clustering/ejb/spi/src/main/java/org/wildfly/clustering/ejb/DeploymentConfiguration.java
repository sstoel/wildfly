/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.ejb;

import org.jboss.modules.Module;
import org.jboss.msc.service.ServiceName;

/**
 * @author Paul Ferraro
 */
public interface DeploymentConfiguration extends org.wildfly.clustering.ee.DeploymentConfiguration {

    /**
     * Returns the service name of the deployment containing the EJB.
     * @return the service name for the deployment
     */
    ServiceName getDeploymentServiceName();

    /**
     * Returns the module of the deployment.
     * @return a module
     */
    Module getModule();
}
