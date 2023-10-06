/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.mod_cluster;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.model.test.FailedOperationTransformationConfig;
import org.jboss.as.model.test.ModelFixer;
import org.jboss.as.model.test.ModelTestControllerVersion;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.KernelServicesBuilder;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * @author Radoslav Husar
 */
@RunWith(Parameterized.class)
public class ModClusterTransformersTestCase extends AbstractSubsystemTest {

    @Parameters
    public static Iterable<ModelTestControllerVersion> parameters() {
        return EnumSet.of(ModelTestControllerVersion.EAP_7_4_0);
    }

    ModelTestControllerVersion version;

    public ModClusterTransformersTestCase(ModelTestControllerVersion version) {
        super(ModClusterExtension.SUBSYSTEM_NAME, new ModClusterExtension());
        this.version = version;
    }

    private static String formatArtifact(String pattern, ModelTestControllerVersion version) {
        return String.format(pattern, version.getMavenGavVersion());
    }

    private static ModClusterSubsystemModel getModelVersion(ModelTestControllerVersion controllerVersion) {
        switch (controllerVersion) {
            case EAP_7_4_0:
                return ModClusterSubsystemModel.VERSION_7_0_0;
        }
        throw new IllegalArgumentException();
    }

    private static String[] getDependencies(ModelTestControllerVersion version) {
        switch (version) {
            case EAP_7_4_0:
                return new String[] {
                        formatArtifact("org.jboss.eap:wildfly-mod_cluster-extension:%s", version),
                        "org.jboss.mod_cluster:mod_cluster-core:1.4.3.Final-redhat-00002",
                        formatArtifact("org.jboss.eap:wildfly-clustering-common:%s", version),
                };
        }
        throw new IllegalArgumentException();
    }

    @Test
    public void testTransformations() throws Exception {
        this.testTransformations(version);
    }

    private void testTransformations(ModelTestControllerVersion controllerVersion) throws Exception {
        ModClusterSubsystemModel model = getModelVersion(controllerVersion);
        ModelVersion modelVersion = model.getVersion();
        String[] dependencies = getDependencies(controllerVersion);


        Set<String> resources = new HashSet<>();
        resources.add(String.format("subsystem-transform-%d_%d_%d.xml", modelVersion.getMajor(), modelVersion.getMinor(), modelVersion.getMicro()));


        for (String resource : resources) {
            String subsystemXml = readResource(resource);

            KernelServicesBuilder builder = createKernelServicesBuilder(new ModClusterAdditionalInitialization())
                    .setSubsystemXml(subsystemXml);
            builder.createLegacyKernelServicesBuilder(null, controllerVersion, modelVersion)
                    .addMavenResourceURL(dependencies)
                    .skipReverseControllerCheck()
                    .dontPersistXml();

            KernelServices mainServices = builder.build();
            KernelServices legacyServices = mainServices.getLegacyServices(modelVersion);

            Assert.assertNotNull(legacyServices);
            Assert.assertTrue(mainServices.isSuccessfulBoot());
            Assert.assertTrue(legacyServices.isSuccessfulBoot());

            checkSubsystemModelTransformation(mainServices, modelVersion, createModelFixer(modelVersion), false);
        }
    }

    private static ModelFixer createModelFixer(ModelVersion version) {
        return model -> {
            if (ModClusterSubsystemModel.VERSION_8_0_0.requiresTransformation(version)) {
                Set.of("default", "with-floating-decay-load-provider").forEach(
                        proxy -> model.get(ProxyConfigurationResourceDefinition.pathElement(proxy).getKeyValuePair()).get("connector").set(new ModelNode())
                );
            }
            return model;
        };
    }

    @Test
    public void testRejections() throws Exception {
        this.testRejections(version);
    }

    private void testRejections(ModelTestControllerVersion controllerVersion) throws Exception {
        String[] dependencies = getDependencies(controllerVersion);
        String subsystemXml = readResource("subsystem-reject.xml");
        ModClusterSubsystemModel model = getModelVersion(controllerVersion);
        ModelVersion modelVersion = model.getVersion();

        KernelServicesBuilder builder = createKernelServicesBuilder(new ModClusterAdditionalInitialization());
        builder.createLegacyKernelServicesBuilder(model.getVersion().getMajor() >= 4 ? new ModClusterAdditionalInitialization() : null, controllerVersion, modelVersion)
                .addSingleChildFirstClass(ModClusterAdditionalInitialization.class)
                .addMavenResourceURL(dependencies)
                .skipReverseControllerCheck();

        KernelServices mainServices = builder.build();
        KernelServices legacyServices = mainServices.getLegacyServices(modelVersion);

        Assert.assertNotNull(legacyServices);
        Assert.assertTrue(mainServices.isSuccessfulBoot());
        Assert.assertTrue(legacyServices.isSuccessfulBoot());

        ModelTestUtils.checkFailedTransformedBootOperations(mainServices, modelVersion, parse(subsystemXml), createFailedOperationConfig(modelVersion));
    }

    private static FailedOperationTransformationConfig createFailedOperationConfig(ModelVersion version) {
        return new FailedOperationTransformationConfig();
    }

}
