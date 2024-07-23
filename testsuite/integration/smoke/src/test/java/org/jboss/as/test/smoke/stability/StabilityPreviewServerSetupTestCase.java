/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.smoke.stability;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.version.Stability;
import org.junit.runner.RunWith;
import org.wildfly.test.stabilitylevel.StabilityServerSetupSnapshotRestoreTasks;

@ServerSetup(StabilityPreviewServerSetupTestCase.PreviewStabilitySetupTask.class)
@RunWith(Arquillian.class)
@RunAsClient
public class StabilityPreviewServerSetupTestCase extends AbstractStabilityServerSetupTaskTest {
    public StabilityPreviewServerSetupTestCase() {
        super(Stability.PREVIEW);
    }


    public static class PreviewStabilitySetupTask extends StabilityServerSetupSnapshotRestoreTasks.Preview {
        @Override
        protected void doSetup(ManagementClient managementClient) throws Exception {
            // Write a system property so the model ges stored with a lower stability level.
            // This is to make sure we can reload back to the higher level from the snapshot
            AbstractStabilityServerSetupTaskTest.addSystemProperty(managementClient, StabilityPreviewServerSetupTestCase.class);
        }
    }

}
