/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.clustering.cluster.cdi.webapp;

import org.infinispan.protostream.SerializationContextInitializer;
import org.infinispan.protostream.annotations.AutoProtoSchemaBuilder;

/**
 * @author Paul Ferraro
 */
@AutoProtoSchemaBuilder(includeClasses = { IncrementorBean.class }, service = false)
public interface CDISerializationContextInitializer extends SerializationContextInitializer {

}
