/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.weld;

import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.msc.service.ServiceName;

public final class ServiceNames {

    public static final ServiceName WELD_START_SERVICE_NAME = ServiceName.of("WeldStartService");

    public static final ServiceName BEAN_MANAGER_SERVICE_NAME = ServiceName.of("beanmanager");

    public static final ServiceName WELD_SECURITY_SERVICES_SERVICE_NAME = ServiceName.of("WeldSecurityServices");

    public static final ServiceName WELD_TRANSACTION_SERVICES_SERVICE_NAME = ServiceName.of("WeldTransactionServices");

    public static final ServiceName WELD_START_COMPLETION_SERVICE_NAME = ServiceName.of("WeldEndInitService");

    /**
     * Gets the Bean Manager MSC service name relative to the Deployment Unit.
     * <p>
     * Modules outside of weld subsystem should use WeldCapability instead to get the name of the Bean Manager service
     * associated to the deployment unit.
     *
     * @param deploymentUnit The deployment unit to be used.
     *
     * @return The Bean Manager service name.
     */
    public static ServiceName beanManagerServiceName(final DeploymentUnit deploymentUnit) {
        return deploymentUnit.getServiceName().append(BEAN_MANAGER_SERVICE_NAME);
    }

    public static ServiceName capabilityServiceName(final DeploymentUnit deploymentUnit, final String baseCapabilityName, final String... dynamicParts) {
        CapabilityServiceSupport capabilityServiceSupport = deploymentUnit.getAttachment(Attachments.CAPABILITY_SERVICE_SUPPORT);
        if (dynamicParts == null || dynamicParts.length == 0) {
            return capabilityServiceSupport.getCapabilityServiceName(baseCapabilityName);
        } else {
            return capabilityServiceSupport.getCapabilityServiceName(baseCapabilityName, dynamicParts);
        }
    }

}
