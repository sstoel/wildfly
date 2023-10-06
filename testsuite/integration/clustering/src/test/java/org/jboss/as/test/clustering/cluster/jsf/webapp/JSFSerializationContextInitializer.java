/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.clustering.cluster.jsf.webapp;

import org.infinispan.protostream.SerializationContextInitializer;
import org.infinispan.protostream.annotations.AutoProtoSchemaBuilder;

/**
 * @author Paul Ferraro
 */
@AutoProtoSchemaBuilder(includeClasses = { Game.class }, service = false)
public interface JSFSerializationContextInitializer extends SerializationContextInitializer {

}
