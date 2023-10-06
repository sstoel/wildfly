/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.service;

import javax.management.MBeanTrustPermission;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.module.ModuleDependency;
import org.jboss.as.server.deployment.module.ModuleSpecification;
import org.jboss.as.service.descriptor.JBossServiceXmlDescriptor;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.security.ImmediatePermissionFactory;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 1.1 $
 */
public class SarModuleDependencyProcessor implements DeploymentUnitProcessor {

    private static final ModuleIdentifier JBOSS_MODULES_ID = ModuleIdentifier.create("org.jboss.modules");
    private static final ModuleIdentifier JBOSS_AS_SYSTEM_JMX_ID = ModuleIdentifier.create("org.jboss.as.system-jmx");
    private static final ModuleIdentifier PROPERTIES_EDITOR_MODULE_ID = ModuleIdentifier.create("org.jboss.common-beans");

    private static final ImmediatePermissionFactory REGISTER_PERMISSION_FACTORY = new ImmediatePermissionFactory(new MBeanTrustPermission("register"));

    /**
     * Add dependencies for modules required for manged bean deployments, if managed bean configurations are attached
     * to the deployment.
     *
     * @param phaseContext the deployment unit context
     * @throws DeploymentUnitProcessingException
     */
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final ModuleSpecification moduleSpecification = deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION);
        final JBossServiceXmlDescriptor serviceXmlDescriptor = deploymentUnit.getAttachment(JBossServiceXmlDescriptor.ATTACHMENT_KEY);
        if(serviceXmlDescriptor == null) {
            return; // Skip deployments with out a service xml descriptor
        }

        moduleSpecification.addSystemDependency(new ModuleDependency(Module.getBootModuleLoader(), JBOSS_MODULES_ID, false, false, false, false));
        moduleSpecification.addSystemDependency(new ModuleDependency(Module.getBootModuleLoader(), JBOSS_AS_SYSTEM_JMX_ID, true, false, false, false));
        // depend on Properties editor module which uses ServiceLoader approach to load the appropriate org.jboss.common.beans.property.finder.PropertyEditorFinder
        moduleSpecification.addSystemDependency(new ModuleDependency(Module.getBootModuleLoader(), PROPERTIES_EDITOR_MODULE_ID, false, false, true, false));

        // All SARs require the ability to register MBeans.
        moduleSpecification.addPermissionFactory(REGISTER_PERMISSION_FACTORY);
    }
}
