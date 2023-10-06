/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.messaging.activemq;

import static org.jboss.as.model.test.ModelTestControllerVersion.EAP_7_4_0;
import static org.junit.Assert.assertTrue;
import static org.wildfly.extension.messaging.activemq.CommonAttributes.BRIDGE;
import static org.wildfly.extension.messaging.activemq.CommonAttributes.DEFAULT;
import static org.wildfly.extension.messaging.activemq.CommonAttributes.SERVER;
import static org.wildfly.extension.messaging.activemq.CommonAttributes.SUBSYSTEM;
import static org.wildfly.extension.messaging.activemq.MessagingDependencies.getActiveMQDependencies;
import static org.wildfly.extension.messaging.activemq.MessagingDependencies.getJGroupsDependencies;
import static org.wildfly.extension.messaging.activemq.MessagingDependencies.getMessagingActiveMQGAV;
import static org.wildfly.extension.messaging.activemq.MessagingExtension.BRIDGE_PATH;
import static org.wildfly.extension.messaging.activemq.MessagingExtension.EXTERNAL_JMS_QUEUE_PATH;
import static org.wildfly.extension.messaging.activemq.MessagingExtension.EXTERNAL_JMS_TOPIC_PATH;
import static org.wildfly.extension.messaging.activemq.MessagingExtension.SERVER_PATH;
import static org.wildfly.extension.messaging.activemq.MessagingExtension.SUBSYSTEM_PATH;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.as.model.test.FailedOperationTransformationConfig;
import org.jboss.as.model.test.ModelTestControllerVersion;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.KernelServicesBuilder;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.clustering.jgroups.spi.JGroupsDefaultRequirement;
import org.wildfly.clustering.server.service.ClusteringDefaultRequirement;
import org.wildfly.clustering.server.service.ClusteringRequirement;
import org.wildfly.extension.messaging.activemq.jms.ConnectionFactoryAttributes;

public class MessagingActiveMQSubsystem_14_0_TestCase extends AbstractSubsystemBaseTest {

    public MessagingActiveMQSubsystem_14_0_TestCase() {
        super(MessagingExtension.SUBSYSTEM_NAME, new MessagingExtension());
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("subsystem_14_0.xml");
    }

    @Override
    protected String getSubsystemXsdPath() throws IOException {
        return "schema/wildfly-messaging-activemq_14_0.xsd";
    }

    @Override
    protected Properties getResolvedProperties() {
        Properties properties = new Properties();
        properties.put("messaging.cluster.user.name", "myClusterUser");
        properties.put("messaging.cluster.user.password", "myClusterPassword");
        return properties;
    }

    @Override
    protected KernelServices standardSubsystemTest(String configId, boolean compareXml) throws Exception {
        return super.standardSubsystemTest(configId, false);
    }

    @Test
    public void testJournalAttributes() throws Exception {
        KernelServices kernelServices = standardSubsystemTest(null, false);
        ModelNode rootModel = kernelServices.readWholeModel();
        ModelNode serverModel = rootModel.require(SUBSYSTEM).require(MessagingExtension.SUBSYSTEM_NAME).require(SERVER)
                .require(DEFAULT);

        Assert.assertEquals(1357, serverModel.get(ServerDefinition.JOURNAL_BUFFER_TIMEOUT.getName()).resolve().asInt());
        Assert.assertEquals(102400, serverModel.get(ServerDefinition.JOURNAL_FILE_SIZE.getName()).resolve().asInt());
        Assert.assertEquals(2, serverModel.get(ServerDefinition.JOURNAL_MIN_FILES.getName()).resolve().asInt());
        Assert.assertEquals(5, serverModel.get(ServerDefinition.JOURNAL_POOL_FILES.getName()).resolve().asInt());
        Assert.assertEquals(7, serverModel.get(ServerDefinition.JOURNAL_FILE_OPEN_TIMEOUT.getName()).resolve().asInt());
        kernelServices.shutdown();
    }

    @Test
    public void testBridgeCallTimeout() throws Exception {
        KernelServices kernelServices = standardSubsystemTest(null, false);
        ModelNode rootModel = kernelServices.readWholeModel();
        ModelNode bridgeModel = rootModel.require(SUBSYSTEM).require(MessagingExtension.SUBSYSTEM_NAME).require(SERVER)
                .require(DEFAULT).require(BRIDGE).require("bridge1");

        Assert.assertEquals("${call.timeout:60000}", bridgeModel.get(BridgeDefinition.CALL_TIMEOUT.getName()).asExpression().getExpressionString());
        Assert.assertEquals(60000, bridgeModel.get(BridgeDefinition.CALL_TIMEOUT.getName()).resolve().asLong());
        kernelServices.shutdown();
    }

    /////////////////////////////////////////
    //  Tests for HA Policy Configuration  //
    /////////////////////////////////////////
    @Test
    public void testHAPolicyConfiguration() throws Exception {
        standardSubsystemTest("subsystem_14_0_ha-policy.xml");
    }

    ///////////////////////
    // Transformers test //
    ///////////////////////
    @Test
    public void testTransformersWildfly26_1() throws Exception {
        testTransformers(ModelTestControllerVersion.MASTER, MessagingExtension.VERSION_13_1_0);
    }

    @Test
    public void testTransformersWildfly25() throws Exception {
        testTransformers(ModelTestControllerVersion.MASTER, MessagingExtension.VERSION_13_0_0);
    }

    @Test
    public void testTransformersEAP_7_4_0() throws Exception {
        testTransformers(EAP_7_4_0, MessagingExtension.VERSION_13_0_0);
    }

    @Test
    public void testRejectingTransformersEAP_7_4_0() throws Exception {
        testRejectingTransformers(EAP_7_4_0, MessagingExtension.VERSION_13_0_0);
    }

    private void testTransformers(ModelTestControllerVersion controllerVersion, ModelVersion messagingVersion) throws Exception {
        //Boot up empty controllers with the resources needed for the ops coming from the xml to work
        KernelServicesBuilder builder = createKernelServicesBuilder(createAdditionalInitialization())
                .setSubsystemXmlResource("subsystem_14_0_transform.xml");
        builder.createLegacyKernelServicesBuilder(createAdditionalInitialization(), controllerVersion, messagingVersion)
                .addMavenResourceURL(getMessagingActiveMQGAV(controllerVersion))
                .addMavenResourceURL(getActiveMQDependencies(controllerVersion))
                .addMavenResourceURL(getJGroupsDependencies(controllerVersion))
                .skipReverseControllerCheck()
                .dontPersistXml();

        KernelServices mainServices = builder.build();
        assertTrue(mainServices.isSuccessfulBoot());
        assertTrue(mainServices.getLegacyServices(messagingVersion).isSuccessfulBoot());

        checkSubsystemModelTransformation(mainServices, messagingVersion, (ModelNode modelNode) -> {
            ModelNode legacyModel = modelNode.clone();
            if (modelNode.hasDefined("server", "default", "address-setting", "test", "page-size-bytes")) {
                int legacyNodeValue = modelNode.get("server", "default", "address-setting", "test", "page-size-bytes").asInt();
                legacyModel.get("server", "default", "address-setting", "test", "page-size-bytes").set(legacyNodeValue);
            }
            return legacyModel;
        });
        mainServices.shutdown();
    }

    private void testRejectingTransformers(ModelTestControllerVersion controllerVersion, ModelVersion messagingVersion) throws Exception {
        //Boot up empty controllers with the resources needed for the ops coming from the xml to work
        KernelServicesBuilder builder = createKernelServicesBuilder(createAdditionalInitialization());
        builder.createLegacyKernelServicesBuilder(createAdditionalInitialization(), controllerVersion, messagingVersion)
                .addMavenResourceURL(getMessagingActiveMQGAV(controllerVersion))
                .addMavenResourceURL(getActiveMQDependencies(controllerVersion))
                .addMavenResourceURL(getJGroupsDependencies(controllerVersion))
                .skipReverseControllerCheck()
                .dontPersistXml();

        KernelServices mainServices = builder.build();
        assertTrue(mainServices.isSuccessfulBoot());
        assertTrue(mainServices.getLegacyServices(messagingVersion).isSuccessfulBoot());

        List<ModelNode> ops = builder.parseXmlResource("subsystem_14_0_reject_transform.xml");
//        System.out.println("ops = " + ops);
        PathAddress subsystemAddress = PathAddress.pathAddress(SUBSYSTEM_PATH);

        FailedOperationTransformationConfig config = new FailedOperationTransformationConfig();

        config.addFailedAttribute(subsystemAddress.append(EXTERNAL_JMS_QUEUE_PATH),
                new FailedOperationTransformationConfig.NewAttributesConfig(ConnectionFactoryAttributes.External.ENABLE_AMQ1_PREFIX));
        config.addFailedAttribute(subsystemAddress.append(EXTERNAL_JMS_TOPIC_PATH),
                new FailedOperationTransformationConfig.NewAttributesConfig(ConnectionFactoryAttributes.External.ENABLE_AMQ1_PREFIX));
        config.addFailedAttribute(subsystemAddress.append(SERVER_PATH, BRIDGE_PATH), new FailedOperationTransformationConfig.NewAttributesConfig(BridgeDefinition.ROUTING_TYPE));
        config.addFailedAttribute(subsystemAddress.append(SERVER_PATH), new FailedOperationTransformationConfig.NewAttributesConfig(
                ServerDefinition.ADDRESS_QUEUE_SCAN_PERIOD
        ));
        ModelTestUtils.checkFailedTransformedBootOperations(mainServices, messagingVersion, ops, config);
        mainServices.shutdown();
    }

    @Override
    protected Set<PathAddress> getIgnoredChildResourcesForRemovalTest() {
        Set<PathAddress> ignoredChildResources = new HashSet<>(super.getIgnoredChildResourcesForRemovalTest());
        ignoredChildResources.add(PathAddress.parseCLIStyleAddress("/subsystem=messaging-activemq/server=default/discovery-group=groupS"));
        return ignoredChildResources;
    }

    @Override
    protected AdditionalInitialization createAdditionalInitialization() {
        return AdditionalInitialization.withCapabilities(ClusteringRequirement.COMMAND_DISPATCHER_FACTORY.resolve("ee"),
                ClusteringDefaultRequirement.COMMAND_DISPATCHER_FACTORY.getName(),
                JGroupsDefaultRequirement.CHANNEL_FACTORY.getName(),
                Capabilities.ELYTRON_DOMAIN_CAPABILITY,
                Capabilities.ELYTRON_DOMAIN_CAPABILITY + ".elytronDomain",
                CredentialReference.CREDENTIAL_STORE_CAPABILITY + ".cs1",
                Capabilities.DATA_SOURCE_CAPABILITY + ".fooDS",
                Capabilities.LEGACY_SECURITY_DOMAIN_CAPABILITY.getDynamicName("other"));
    }
}
