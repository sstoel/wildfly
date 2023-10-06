/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.clustering.jgroups.subsystem;

import org.jboss.as.clustering.jgroups.subsystem.EncryptProtocolResourceDefinition.Attribute;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;

/**
 * Transformer for encrypt protocol resources.
 * @author Paul Ferraro
 */
public class EncryptProtocolResourceTransformer extends ProtocolResourceTransformer {

    EncryptProtocolResourceTransformer(ResourceTransformationDescriptionBuilder builder) {
        super(builder);
    }

    @Override
    public void accept(ModelVersion version) {

        if (JGroupsSubsystemModel.VERSION_8_0_0.requiresTransformation(version)) {
            this.builder.getAttributeBuilder()
                    .addRejectCheck(CredentialReference.REJECT_CREDENTIAL_REFERENCE_WITH_BOTH_STORE_AND_CLEAR_TEXT, Attribute.KEY_CREDENTIAL.getName())
                    .end();
        }

        super.accept(version);
    }
}
