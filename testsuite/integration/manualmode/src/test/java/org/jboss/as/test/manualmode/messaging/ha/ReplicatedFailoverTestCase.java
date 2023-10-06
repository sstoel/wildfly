/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.manualmode.messaging.ha;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.security.AccessController;
import java.security.PrivilegedAction;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.integration.common.jms.JMSOperations;
import org.jboss.as.test.integration.common.jms.JMSOperationsProvider;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jgroups.util.Util;
import org.junit.Assume;
import org.junit.BeforeClass;

/**
 *
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2015 Red Hat inc.
 */
public class ReplicatedFailoverTestCase extends FailoverTestCase {
    private static final ModelNode PRIMARY_STORE_ADDRESS = PathAddress.parseCLIStyleAddress("/subsystem=messaging-activemq/server=default/ha-policy=replication-primary").toModelNode();
    private static final ModelNode SECONDARY_STORE_ADDRESS = PathAddress.parseCLIStyleAddress("/subsystem=messaging-activemq/server=default/ha-policy=replication-secondary").toModelNode();

    @BeforeClass
    public static void beforeClass() {
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            Assume.assumeFalse("[WFLY-14481] Disable on Windows", Util.checkForWindows());
            return null;
        });
    }

    @Override
    protected void setUpServer1(ModelControllerClient client) throws Exception {
        configureCluster(client);

        // /subsystem=messaging-activemq/server=default/ha-policy=replication-primary:add(cluster-name=my-cluster, check-for-live-server=true)
        ModelNode operation = Operations.createAddOperation(PRIMARY_STORE_ADDRESS);
        operation.get("cluster-name").set("my-cluster");
        operation.get("check-for-live-server").set(true);
        execute(client, operation);

        JMSOperations jmsOperations = JMSOperationsProvider.getInstance(client);
        jmsOperations.createJmsQueue(jmsQueueName, "java:jboss/exported/" + jmsQueueLookup);
//        jmsOperations.enableMessagingTraces();
    }

    @Override
    protected void setUpServer2(ModelControllerClient client) throws Exception {
        configureCluster(client);

        // /subsystem=messaging-activemq/server=default/ha-policy=replication-secondary:add(cluster-name=my-cluster, restart-backup=true)
        ModelNode operation = Operations.createAddOperation(SECONDARY_STORE_ADDRESS);
        operation.get("cluster-name").set("my-cluster");
        operation.get("restart-backup").set(true);
        execute(client, operation);

        JMSOperations jmsOperations = JMSOperationsProvider.getInstance(client);
        jmsOperations.createJmsQueue(jmsQueueName, "java:jboss/exported/" + jmsQueueLookup);
//        jmsOperations.enableMessagingTraces();
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        // leave some time after servers are setup and reloaded so that the cluster is formed
        Thread.sleep(TimeoutUtil.adjust(2000));
    }

    private void configureCluster(ModelControllerClient client) throws Exception {
        // /subsystem=messaging-activemq/server=default:write-attribute(name=cluster-user, value=clusteruser)
        ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).add(SUBSYSTEM, "messaging-activemq");
        operation.get(OP_ADDR).add("server", "default");
        operation.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        operation.get(NAME).set("cluster-password");
        operation.get(VALUE).set("clusterpassword");
        execute(client, operation);

        // /subsystem=messaging-activemq/server=default:write-attribute(name=cluster-password, value=clusterpwd)
        operation = new ModelNode();
        operation.get(OP_ADDR).add(SUBSYSTEM, "messaging-activemq");
        operation.get(OP_ADDR).add("server", "default");
        operation.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        operation.get(NAME).set("cluster-user");
        operation.get(VALUE).set("clusteruser");
        execute(client, operation);
    }

    @Override
    protected void testPrimaryInSyncWithReplica(ModelControllerClient client) throws Exception {
        ModelNode operation = Operations.createReadAttributeOperation(
                PathAddress.parseCLIStyleAddress("/subsystem=messaging-activemq/server=default/ha-policy=replication-primary").toModelNode(),
                "synchronized-with-backup");
        boolean synced = false;
        long start = System.currentTimeMillis();
        while (!synced && (System.currentTimeMillis() - start < TimeoutUtil.adjust(10000))) {
            synced = execute(client, operation).asBoolean();
        }
        assertTrue(synced);
    }

    @Override
    protected void testSecondaryInSyncWithReplica(ModelControllerClient client) throws Exception {
        ModelNode operation = Operations.createReadAttributeOperation(
                PathAddress.parseCLIStyleAddress("/subsystem=messaging-activemq/server=default/ha-policy=replication-secondary").toModelNode(),
                "synchronized-with-live");
        assertTrue(execute(client, operation).asBoolean());
    }

    @Override
    protected void testPrimaryOutOfSyncWithReplica(ModelControllerClient client) throws Exception {
        ModelNode operation = Operations.createReadAttributeOperation(
                PathAddress.parseCLIStyleAddress("/subsystem=messaging-activemq/server=default/ha-policy=replication-primary").toModelNode(),
                "synchronized-with-backup");
        boolean synced = false;
        long start = System.currentTimeMillis();
        while (!synced && (System.currentTimeMillis() - start < TimeoutUtil.adjust(10000))) {
            synced = execute(client, operation).asBoolean();
        }
        assertFalse(synced);
    }

    @Override
    protected void testSecondaryOutOfSyncWithReplica(ModelControllerClient client) throws Exception {
        ModelNode operation = Operations.createReadAttributeOperation(
                PathAddress.parseCLIStyleAddress("/subsystem=messaging-activemq/server=default/ha-policy=replication-secondary").toModelNode(),
                "synchronized-with-live");
        assertFalse(execute(client, operation).asBoolean());
    }
}
