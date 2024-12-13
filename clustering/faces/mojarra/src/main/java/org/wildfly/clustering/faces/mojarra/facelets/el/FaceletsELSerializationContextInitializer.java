/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.faces.mojarra.facelets.el;

import org.kohsuke.MetaInfServices;
import org.wildfly.clustering.marshalling.protostream.AbstractSerializationContextInitializer;
import org.wildfly.clustering.marshalling.protostream.SerializationContext;
import org.wildfly.clustering.marshalling.protostream.SerializationContextInitializer;

/**
 * @author Paul Ferraro
 */
@MetaInfServices(SerializationContextInitializer.class)
public class FaceletsELSerializationContextInitializer extends AbstractSerializationContextInitializer {

    public FaceletsELSerializationContextInitializer() {
        super("com.sun.faces.facelets.el.proto");
    }

    @Override
    public void registerMarshallers(SerializationContext context) {
        context.registerMarshaller(new ContextualCompositeValueExpressionMarshaller());
        context.registerMarshaller(new TagMethodExpressionMarshaller());
        context.registerMarshaller(new TagValueExpressionMarshaller());
    }
}
