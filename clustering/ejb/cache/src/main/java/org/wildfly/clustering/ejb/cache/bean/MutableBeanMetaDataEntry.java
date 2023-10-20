/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.ejb.cache.bean;

import java.time.Instant;

import org.wildfly.clustering.ee.cache.offset.OffsetValue;
import org.wildfly.clustering.ee.cache.offset.Value;

/**
 * @author Paul Ferraro
 */
public class MutableBeanMetaDataEntry<K> implements BeanMetaDataEntry<K> {

    private final ImmutableBeanMetaDataEntry<K> entry;
    private final OffsetValue<Instant> lastAccess;

    public MutableBeanMetaDataEntry(ImmutableBeanMetaDataEntry<K> entry, OffsetValue<Instant> lastAccess) {
        this.entry = entry;
        this.lastAccess = lastAccess;
    }

    @Override
    public String getName() {
        return this.entry.getName();
    }

    @Override
    public K getGroupId() {
        return this.entry.getGroupId();
    }

    @Override
    public Value<Instant> getLastAccess() {
        return this.lastAccess;
    }
}
