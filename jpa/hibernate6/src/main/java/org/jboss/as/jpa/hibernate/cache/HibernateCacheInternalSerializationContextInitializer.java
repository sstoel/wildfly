/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.jpa.hibernate.cache;

import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.SerializationContextInitializer;
import org.kohsuke.MetaInfServices;
import org.wildfly.clustering.marshalling.protostream.AbstractSerializationContextInitializer;

/**
 * {@link SerializationContextInitializer} for the {@link org.hibernate.cache.internal} package.
 * @author Paul Ferraro
 */
@MetaInfServices(SerializationContextInitializer.class)
public class HibernateCacheInternalSerializationContextInitializer extends AbstractSerializationContextInitializer {

    public HibernateCacheInternalSerializationContextInitializer() {
        super("org.hibernate.cache.internal.proto");
    }

    @Override
    public void registerMarshallers(SerializationContext context) {
        context.registerMarshaller(new BasicCacheKeyImplementationMarshaller());
        context.registerMarshaller(new CacheKeyImplementationMarshaller());
        context.registerMarshaller(new NaturalIdCacheKeyMarshaller());
    }
}
