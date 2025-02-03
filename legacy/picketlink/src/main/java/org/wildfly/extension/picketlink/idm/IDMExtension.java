/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.picketlink.idm;

import static org.wildfly.extension.picketlink.idm.Namespace.CURRENT;
import static org.wildfly.extension.picketlink.idm.Namespace.PICKETLINK_IDENTITY_MANAGEMENT_1_0;
import static org.wildfly.extension.picketlink.idm.Namespace.PICKETLINK_IDENTITY_MANAGEMENT_1_1;

import java.util.Collections;
import java.util.Set;

import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.descriptions.DeprecatedResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.extension.AbstractLegacyExtension;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.transform.ExtensionTransformerRegistration;
import org.jboss.as.controller.transform.SubsystemTransformerRegistration;
import org.jboss.as.controller.transform.description.DiscardAttributeChecker;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.TransformationDescription.Tools;
import org.jboss.as.controller.transform.description.TransformationDescriptionBuilder;
import org.wildfly.extension.picketlink.idm.model.IdentityConfigurationResourceDefinition;
import org.wildfly.extension.picketlink.idm.model.LDAPStoreResourceDefinition;
import org.wildfly.extension.picketlink.idm.model.PartitionManagerResourceDefinition;

/**
 * @author <a href="mailto:psilva@redhat.com">Pedro Silva</a>
 */
public class IDMExtension extends AbstractLegacyExtension {

    public static final String SUBSYSTEM_NAME = "picketlink-identity-management";
    private static final String RESOURCE_NAME = IDMExtension.class.getPackage().getName() + ".LocalDescriptions";

    private static final ModelVersion CURRENT_MODEL_VERSION = ModelVersion.create(CURRENT.getMajor(), CURRENT.getMinor());

    //deprecated in EAP 6.4
    public static final ModelVersion DEPRECATED_SINCE = ModelVersion.create(2,0,0);

    public IDMExtension() {
        super("org.wildfly.extension.picketlink", SUBSYSTEM_NAME);
    }

    public static ResourceDescriptionResolver getResourceDescriptionResolver(final String keyPrefix) {
        return new DeprecatedResourceDescriptionResolver(SUBSYSTEM_NAME, keyPrefix, RESOURCE_NAME, IDMExtension.class.getClassLoader(), true, true);
    }

    @Override
    protected Set<ManagementResourceRegistration> initializeLegacyModel(ExtensionContext context) {
        SubsystemRegistration subsystem = context.registerSubsystem(SUBSYSTEM_NAME, CURRENT_MODEL_VERSION, true);

        ManagementResourceRegistration mrr = subsystem.registerSubsystemModel(IDMSubsystemRootResourceDefinition.INSTANCE);
        subsystem.registerXMLElementWriter(Namespace.CURRENT.getXMLWriter());

        return Collections.singleton(mrr);
    }

    @Override
    protected void initializeLegacyParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.CURRENT.getUri(), Namespace.CURRENT::getXMLReader);
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, PICKETLINK_IDENTITY_MANAGEMENT_1_1.getUri(), PICKETLINK_IDENTITY_MANAGEMENT_1_1::getXMLReader);
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, PICKETLINK_IDENTITY_MANAGEMENT_1_0.getUri(), PICKETLINK_IDENTITY_MANAGEMENT_1_0::getXMLReader);

    }

    public static final class TransformerRegistration implements ExtensionTransformerRegistration {

        @Override
        public String getSubsystemName() {
            return SUBSYSTEM_NAME;
        }

        @Override
        public void registerTransformers(SubsystemTransformerRegistration subsystemRegistration) {
            ResourceTransformationDescriptionBuilder builder = TransformationDescriptionBuilder.Factory.createSubsystemInstance();
            ResourceTransformationDescriptionBuilder partitionManagerResourceBuilder = builder
                    .addChildResource(PartitionManagerResourceDefinition.INSTANCE);
            ResourceTransformationDescriptionBuilder identityConfigResourceBuilder = partitionManagerResourceBuilder
                    .addChildResource(IdentityConfigurationResourceDefinition.INSTANCE);
            ResourceTransformationDescriptionBuilder ldapTransfDescBuilder = identityConfigResourceBuilder
                    .addChildResource(LDAPStoreResourceDefinition.INSTANCE);

            ldapTransfDescBuilder.getAttributeBuilder()
                    .addRejectCheck(RejectAttributeChecker.DEFINED, LDAPStoreResourceDefinition.ACTIVE_DIRECTORY)
                    .setDiscard(DiscardAttributeChecker.DEFAULT_VALUE, LDAPStoreResourceDefinition.ACTIVE_DIRECTORY);

            ldapTransfDescBuilder.getAttributeBuilder().addRejectCheck(RejectAttributeChecker.DEFINED,
                            LDAPStoreResourceDefinition.UNIQUE_ID_ATTRIBUTE_NAME)
                    .setDiscard(DiscardAttributeChecker.UNDEFINED, LDAPStoreResourceDefinition.UNIQUE_ID_ATTRIBUTE_NAME);

            Tools.register(builder.build(), subsystemRegistration, ModelVersion.create(1, 0));
        }
    }
}
