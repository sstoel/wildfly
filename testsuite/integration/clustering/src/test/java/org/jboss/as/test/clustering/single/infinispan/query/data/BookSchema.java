/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.clustering.single.infinispan.query.data;

import org.infinispan.protostream.GeneratedSchema;
import org.infinispan.protostream.annotations.AutoProtoSchemaBuilder;

/**
 * Adopted and adapted from Infinispan testsuite.
 *
 * @author Radoslav Husar
 * @since 27
 */
@AutoProtoSchemaBuilder(includeClasses = { Book.class }, service = false)
public interface BookSchema extends GeneratedSchema {
}
