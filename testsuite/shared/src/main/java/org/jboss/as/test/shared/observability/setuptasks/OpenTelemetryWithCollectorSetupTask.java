/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.shared.observability.setuptasks;

import org.jboss.arquillian.testcontainers.api.DockerRequired;
import org.jboss.arquillian.testcontainers.api.Testcontainer;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.test.shared.ServerReload;
import org.jboss.as.test.shared.observability.containers.OpenTelemetryCollectorContainer;

@DockerRequired
public class OpenTelemetryWithCollectorSetupTask extends OpenTelemetrySetupTask {

    @Testcontainer
    private OpenTelemetryCollectorContainer otelCollectorContainer;

    @Override
    public void setup(final ManagementClient managementClient, final String containerId) throws Exception {
        super.setup(managementClient, containerId);
        executeOp(managementClient, writeAttribute(SUBSYSTEM_NAME, "endpoint", otelCollectorContainer.getOtlpGrpcEndpoint()));

        ServerReload.executeReloadAndWaitForCompletion(managementClient);
    }

    @Override
    public void tearDown(final ManagementClient managementClient, final String containerId) throws Exception {
        otelCollectorContainer.stop();
        super.tearDown(managementClient, containerId);

        ServerReload.executeReloadAndWaitForCompletion(managementClient);
    }
}
