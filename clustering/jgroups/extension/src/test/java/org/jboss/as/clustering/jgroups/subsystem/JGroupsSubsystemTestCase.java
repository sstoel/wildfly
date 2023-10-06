/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.clustering.jgroups.subsystem;

import java.util.EnumSet;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.clustering.controller.CommonUnaryRequirement;
import org.jboss.as.clustering.subsystem.AdditionalInitialization;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.as.subsystem.test.AbstractSubsystemSchemaTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.KernelServicesBuilder;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests parsing / booting / marshalling of JGroups configurations.
 *
 * The current XML configuration is tested, along with supported legacy configurations.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Richard Achmatowicz (c) 2013 Red Hat Inc.
 */
@RunWith(value = Parameterized.class)
public class JGroupsSubsystemTestCase extends AbstractSubsystemSchemaTest<JGroupsSubsystemSchema> {

    @Parameters
    public static Iterable<JGroupsSubsystemSchema> parameters() {
        return EnumSet.allOf(JGroupsSubsystemSchema.class);
    }

    private final JGroupsSubsystemSchema schema;

    public JGroupsSubsystemTestCase(JGroupsSubsystemSchema schema) {
        super(JGroupsExtension.SUBSYSTEM_NAME, new JGroupsExtension(), schema, JGroupsSubsystemSchema.CURRENT);
        this.schema = schema;
    }

    @Override
    protected String getSubsystemXsdPathPattern() {
        return "schema/jboss-as-%s_%d_%d.xsd";
    }

    private KernelServices buildKernelServices() throws Exception {
        return this.buildKernelServices(this.getSubsystemXml());
    }

    private KernelServices buildKernelServices(String xml) throws Exception {
        return this.createKernelServicesBuilder(xml).build();
    }

    private KernelServicesBuilder createKernelServicesBuilder() {
        return this.createKernelServicesBuilder(this.createAdditionalInitialization());
    }

    private KernelServicesBuilder createKernelServicesBuilder(String xml) throws XMLStreamException {
        return this.createKernelServicesBuilder().setSubsystemXml(xml);
    }

    @Override
    protected org.jboss.as.subsystem.test.AdditionalInitialization createAdditionalInitialization() {
        return new AdditionalInitialization()
                .require(CommonUnaryRequirement.SOCKET_BINDING, "jgroups-tcp", "jgroups-udp", "some-binding", "jgroups-diagnostics", "jgroups-mping", "jgroups-tcp-fd", "jgroups-client-fd")
                .require(CommonUnaryRequirement.OUTBOUND_SOCKET_BINDING, "node1", "node2")
                .require(CommonUnaryRequirement.KEY_STORE, "my-key-store")
                .require(CommonUnaryRequirement.CREDENTIAL_STORE, "my-credential-store")
                .require(CommonUnaryRequirement.DATA_SOURCE, "ExampleDS")
                ;
    }

    /**
     * Tests that the 'fork' and 'stack' resources allow indexed adds for the 'protocol' children. This is important for
     * the work being done for WFCORE-401. This work involves calculating the operations to bring the secondary Host Controller's domain model
     * into sync with the primary Host Controller's domain model. Without ordered resources, that would mean on reconnect if the primary
     * had added a protocol somewhere in the middle, the protocol would get added to the end rather at the correct place.
     */
    @Test
    public void testIndexedAdds() throws Exception {
        if (!this.schema.since(JGroupsSubsystemSchema.VERSION_3_0)) return;

        final KernelServices services = this.buildKernelServices();

        ModelNode originalSubsystemModel = services.readWholeModel().get(JGroupsSubsystemResourceDefinition.PATH.getKeyValuePair());
        ModelNode originalChannelModel = originalSubsystemModel.get(ChannelResourceDefinition.pathElement("ee").getKeyValuePair());
        ModelNode originalForkModel = originalChannelModel.get(ForkResourceDefinition.pathElement("web").getKeyValuePair());

        Assert.assertTrue(originalForkModel.isDefined());
        originalForkModel.protect();
        Assert.assertTrue(0 < originalForkModel.get(ProtocolResourceDefinition.WILDCARD_PATH.getKey()).keys().size());

        ModelNode originalStackModel = originalSubsystemModel.get(StackResourceDefinition.pathElement("maximal").getKeyValuePair());
        Assert.assertTrue(originalStackModel.isDefined());
        originalStackModel.protect();


        final PathAddress subsystemAddress = PathAddress.pathAddress(JGroupsSubsystemResourceDefinition.PATH);
        final PathAddress forkAddress = subsystemAddress.append(ChannelResourceDefinition.pathElement("ee")).append(ForkResourceDefinition.pathElement("web"));
        final PathAddress stackAddress = subsystemAddress.append(StackResourceDefinition.pathElement("maximal"));

        //Check the fork protocols honour indexed adds by inserting a protocol at the start
        ModelNode add = Util.createAddOperation(forkAddress.append(ProtocolResourceDefinition.pathElement("MERGE3")), 0);
        ModelTestUtils.checkOutcome(services.executeOperation(add));

        ModelNode subsystemModel = services.readWholeModel().get(JGroupsSubsystemResourceDefinition.PATH.getKeyValuePair());
        ModelNode channelModel = subsystemModel.get(ChannelResourceDefinition.pathElement("ee").getKeyValuePair());
        ModelNode forkModel = channelModel.get(ForkResourceDefinition.pathElement("web").getKeyValuePair());

        Assert.assertEquals(originalForkModel.keys().size() + 1, forkModel.get(ProtocolResourceDefinition.WILDCARD_PATH.getKey()).keys().size());
        Assert.assertEquals("MERGE3", forkModel.get(ProtocolResourceDefinition.WILDCARD_PATH.getKey()).keys().iterator().next());

        //Check the stack protocols honour indexed adds by removing a protocol in the middle and readding it
        ModelNode remove = Util.createRemoveOperation(stackAddress.append(ProtocolResourceDefinition.pathElement("FD")));
        ModelTestUtils.checkOutcome(services.executeOperation(remove));
        add = Util.createAddOperation(stackAddress.append(ProtocolResourceDefinition.pathElement("FD")), 3); //The original index of the FD protocol
        ModelTestUtils.checkOutcome(services.executeOperation(add));

        subsystemModel = services.readWholeModel().get(JGroupsSubsystemResourceDefinition.PATH.getKeyValuePair());
        ModelNode stackModel = subsystemModel.get(StackResourceDefinition.pathElement("maximal").getKeyValuePair());
        Assert.assertEquals(originalStackModel, stackModel);
    }
}
