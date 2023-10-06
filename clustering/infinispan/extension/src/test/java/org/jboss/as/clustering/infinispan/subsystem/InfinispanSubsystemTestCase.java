/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.clustering.infinispan.subsystem;

import java.util.EnumSet;
import java.util.Properties;

import org.jboss.as.clustering.controller.CommonRequirement;
import org.jboss.as.clustering.controller.CommonUnaryRequirement;
import org.jboss.as.clustering.jgroups.subsystem.JGroupsSubsystemResourceDefinition;
import org.jboss.as.subsystem.test.AbstractSubsystemSchemaTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.wildfly.clustering.jgroups.spi.JGroupsDefaultRequirement;

/**
 * Tests parsing / booting / marshalling of Infinispan configurations.
 *
 * The current XML configuration is tested, along with supported legacy configurations.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Richard Achmatowicz (c) 2013 Red Hat Inc.
 */
@RunWith(value = Parameterized.class)
public class InfinispanSubsystemTestCase extends AbstractSubsystemSchemaTest<InfinispanSubsystemSchema> {

    @Parameters
    public static Iterable<InfinispanSubsystemSchema> parameters() {
        return EnumSet.allOf(InfinispanSubsystemSchema.class);
    }

    private final InfinispanSubsystemSchema schema;

    public InfinispanSubsystemTestCase(InfinispanSubsystemSchema schema) {
        super(InfinispanExtension.SUBSYSTEM_NAME, new InfinispanExtension(), schema, InfinispanSubsystemSchema.CURRENT);
        this.schema = schema;
    }

    @Override
    protected String getSubsystemXsdPathPattern() {
        return "schema/jboss-as-%s_%d_%d.xsd";
    }

    @Override
    protected AdditionalInitialization createAdditionalInitialization() {
        return new InfinispanSubsystemInitialization()
                .require(CommonUnaryRequirement.OUTBOUND_SOCKET_BINDING, "hotrod-server-1", "hotrod-server-2")
                .require(CommonUnaryRequirement.DATA_SOURCE, "ExampleDS")
                .require(CommonUnaryRequirement.PATH, "jboss.server.temp.dir")
                .require(JGroupsDefaultRequirement.CHANNEL_FACTORY)
                .require(CommonRequirement.LOCAL_TRANSACTION_PROVIDER)
                .require(TransactionResourceDefinition.TransactionRequirement.XA_RESOURCE_RECOVERY_REGISTRY)
                ;
    }

    @Override
    protected void compare(ModelNode model1, ModelNode model2) {
        purgeJGroupsModel(model1);
        purgeJGroupsModel(model2);
        super.compare(model1, model2);
    }

    private static void purgeJGroupsModel(ModelNode model) {
        model.get(JGroupsSubsystemResourceDefinition.PATH.getKey()).remove(JGroupsSubsystemResourceDefinition.PATH.getValue());
    }

    @Override
    protected Properties getResolvedProperties() {
        Properties properties = new Properties();
        properties.put("java.io.tmpdir", "/tmp");
        return properties;
    }

    @Override
    protected KernelServices standardSubsystemTest(String configId, String configIdResolvedModel, boolean compareXml, AdditionalInitialization additionalInit) throws Exception {
        KernelServices services = super.standardSubsystemTest(configId, configIdResolvedModel, compareXml, additionalInit);

        if (!this.schema.since(InfinispanSubsystemSchema.VERSION_1_5)) {
            ModelNode model = services.readWholeModel();

            Assert.assertTrue(model.get(InfinispanSubsystemResourceDefinition.PATH.getKey()).hasDefined(InfinispanSubsystemResourceDefinition.PATH.getValue()));
            ModelNode subsystem = model.get(InfinispanSubsystemResourceDefinition.PATH.getKeyValuePair());

            for (Property containerProp : subsystem.get(CacheContainerResourceDefinition.WILDCARD_PATH.getKey()).asPropertyList()) {
                Assert.assertTrue("cache-container=" + containerProp.getName(),
                        containerProp.getValue().get(CacheContainerResourceDefinition.Attribute.STATISTICS_ENABLED.getName()).asBoolean());

                for (String key : containerProp.getValue().keys()) {
                    if (key.endsWith("-cache") && !key.equals("default-cache")) {
                        ModelNode caches = containerProp.getValue().get(key);
                        if (caches.isDefined()) {
                            for (Property cacheProp : caches.asPropertyList()) {
                                Assert.assertTrue("cache-container=" + containerProp.getName() + "," + key + "=" + cacheProp.getName(),
                                        containerProp.getValue().get(CacheResourceDefinition.Attribute.STATISTICS_ENABLED.getName()).asBoolean());
                            }
                        }
                    }
                }
            }
        }

        return services;
    }
}
