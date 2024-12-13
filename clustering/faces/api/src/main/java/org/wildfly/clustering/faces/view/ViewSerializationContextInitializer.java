/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.faces.view;

import org.kohsuke.MetaInfServices;
import org.wildfly.clustering.marshalling.protostream.AbstractSerializationContextInitializer;
import org.wildfly.clustering.marshalling.protostream.SerializationContext;
import org.wildfly.clustering.marshalling.protostream.SerializationContextInitializer;

/**
 * @author Paul Ferraro
 */
@MetaInfServices(SerializationContextInitializer.class)
public class ViewSerializationContextInitializer extends AbstractSerializationContextInitializer {

    public ViewSerializationContextInitializer() {
        super("jakarta.faces.view.proto");
    }

    @Override
    public void registerMarshallers(SerializationContext context) {
        context.registerMarshaller(new LocationMarshaller());
    }
}
