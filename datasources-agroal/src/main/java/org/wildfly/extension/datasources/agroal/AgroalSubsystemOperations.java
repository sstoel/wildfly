/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.datasources.agroal;

import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.as.server.deployment.Phase;
import org.jboss.dmr.ModelNode;
import org.wildfly.extension.datasources.agroal.deployment.DataSourceDefinitionAnnotationProcessor;
import org.wildfly.extension.datasources.agroal.deployment.DataSourceDefinitionDescriptorProcessor;
import org.wildfly.extension.datasources.agroal.logging.AgroalLogger;

/**
 * Operations for adding and removing the subsystem resource to the model
 *
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
class AgroalSubsystemOperations {

    static final OperationStepHandler ADD_OPERATION = new AgroalSubsystemAdd();

    private static class AgroalSubsystemAdd extends AbstractBoottimeAddStepHandler {

        @Override
        protected void performBoottime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            context.addStep(new AbstractDeploymentChainStep() {
                public void execute(DeploymentProcessorTarget processorTarget) {
                    AgroalLogger.SERVICE_LOGGER.addingDeploymentProcessors();
                    processorTarget.addDeploymentProcessor(AgroalExtension.SUBSYSTEM_NAME, Phase.PARSE, Phase.PARSE_RESOURCE_DEF_ANNOTATION_DATA_SOURCE, new DataSourceDefinitionAnnotationProcessor());
                    processorTarget.addDeploymentProcessor(AgroalExtension.SUBSYSTEM_NAME, Phase.POST_MODULE, Phase.POST_MODULE_RESOURCE_DEF_XML_DATA_SOURCE, new DataSourceDefinitionDescriptorProcessor());
                }
            }, OperationContext.Stage.RUNTIME);
        }
    }

}
