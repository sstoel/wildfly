/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.picketlink.federation;

import static org.wildfly.extension.picketlink.federation.Namespace.CURRENT;
import static org.wildfly.extension.picketlink.federation.Namespace.PICKETLINK_FEDERATION_1_1;
import static org.wildfly.extension.picketlink.federation.Namespace.PICKETLINK_FEDERATION_1_0;

import java.util.Collections;
import java.util.Set;

import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.DeprecatedResourceDescriptionResolver;
import org.jboss.as.controller.extension.AbstractLegacyExtension;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.transform.ExtensionTransformerRegistration;
import org.jboss.as.controller.transform.SubsystemTransformerRegistration;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.TransformationDescription;
import org.jboss.as.controller.transform.description.TransformationDescriptionBuilder;
import org.wildfly.extension.picketlink.federation.model.FederationResourceDefinition;
import org.wildfly.extension.picketlink.federation.model.keystore.KeyResourceDefinition;
import org.wildfly.extension.picketlink.federation.model.keystore.KeyStoreProviderResourceDefinition;
import org.wildfly.extension.picketlink.federation.model.parser.FederationSubsystemWriter;

/**
 * @author <a href="mailto:psilva@redhat.com">Pedro Silva</a>
 */
public final class FederationExtension extends AbstractLegacyExtension {

    public static final String SUBSYSTEM_NAME = "picketlink-federation";
    public static final PathElement SUBSYSTEM_PATH = PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, SUBSYSTEM_NAME);
    private static final String RESOURCE_NAME = FederationExtension.class.getPackage().getName() + ".LocalDescriptions";

    private static final ModelVersion CURRENT_MODEL_VERSION = ModelVersion.create(CURRENT.getMajor(), CURRENT.getMinor());

    //deprecated in EAP 6.4
    public static final ModelVersion DEPRECATED_SINCE = ModelVersion.create(2,0,0);

    public FederationExtension() {
        super("org.wildfly.extension.picketlink", SUBSYSTEM_NAME);
    }

    public static ResourceDescriptionResolver getResourceDescriptionResolver(final String keyPrefix) {
        return new DeprecatedResourceDescriptionResolver(SUBSYSTEM_NAME, keyPrefix, RESOURCE_NAME, FederationExtension.class.getClassLoader(), true, true);
    }

    @Override
    protected Set<ManagementResourceRegistration> initializeLegacyModel(ExtensionContext context) {
        SubsystemRegistration subsystemRegistration = context.registerSubsystem(SUBSYSTEM_NAME, CURRENT_MODEL_VERSION, true);

        final ManagementResourceRegistration subsystem = subsystemRegistration.registerSubsystemModel(new FederationSubsystemRootResourceDefinition());
        subsystemRegistration.registerXMLElementWriter(FederationSubsystemWriter.INSTANCE);

        return Collections.singleton(subsystem);
    }

    @Override
    protected void initializeLegacyParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, CURRENT.getUri(), CURRENT::getXMLReader);
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, PICKETLINK_FEDERATION_1_1.getUri(), PICKETLINK_FEDERATION_1_1::getXMLReader);
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, PICKETLINK_FEDERATION_1_0.getUri(), PICKETLINK_FEDERATION_1_0::getXMLReader);
    }

    public static final class TransformerRegistration implements ExtensionTransformerRegistration {

        @Override
        public String getSubsystemName() {
            return SUBSYSTEM_NAME;
        }

        @Override
        public void registerTransformers(SubsystemTransformerRegistration subsystemRegistration) {
            ResourceTransformationDescriptionBuilder builder = TransformationDescriptionBuilder.Factory.createSubsystemInstance();
            ResourceTransformationDescriptionBuilder federationTransfDescBuilder = builder
                    .addChildResource(new FederationResourceDefinition());
            ResourceTransformationDescriptionBuilder keyStoreTransfDescBuilder = federationTransfDescBuilder
                    .addChildResource(KeyStoreProviderResourceDefinition.INSTANCE);

            keyStoreTransfDescBuilder.rejectChildResource(KeyResourceDefinition.INSTANCE.getPathElement());

            TransformationDescription.Tools.register(builder.build(), subsystemRegistration, ModelVersion.create(1, 0));

        }
    }
}
