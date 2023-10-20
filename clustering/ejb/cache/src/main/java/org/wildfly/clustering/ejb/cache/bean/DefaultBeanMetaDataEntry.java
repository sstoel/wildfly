/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.ejb.cache.bean;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;

import org.wildfly.clustering.ee.cache.offset.Offset;
import org.wildfly.clustering.ee.cache.offset.OffsetValue;

/**
 * @author Paul Ferraro
 */
public class DefaultBeanMetaDataEntry<K> implements RemappableBeanMetaDataEntry<K> {

    private final String name;
    private final K groupId;
    private final OffsetValue<Instant> lastAccess;

    public DefaultBeanMetaDataEntry(String name, K groupId) {
        this(name, groupId, Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    DefaultBeanMetaDataEntry(String name, K groupId, Instant creationTime) {
        this.name = name;
        this.groupId = groupId;
        this.lastAccess = OffsetValue.from(creationTime);
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public K getGroupId() {
        return this.groupId;
    }

    @Override
    public OffsetValue<Instant> getLastAccess() {
        return this.lastAccess;
    }

    @Override
    public RemappableBeanMetaDataEntry<K> remap(Supplier<Offset<Instant>> lastAccessOffset) {
        RemappableBeanMetaDataEntry<K> result = new DefaultBeanMetaDataEntry<>(this.name, this.groupId, this.lastAccess.getBasis());
        result.getLastAccess().set(lastAccessOffset.get().apply(this.lastAccess.get()));
        return result;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(this.getClass().getSimpleName()).append(" { ");
        builder.append("name = ").append(this.name);
        builder.append(", group = ").append(this.groupId);
        builder.append(", created = ").append(this.lastAccess.getBasis());
        builder.append(", last-access = ").append(this.lastAccess.get());
        return builder.append(" }").toString();
    }
}
