/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.ejb.infinispan.bean;

import org.wildfly.clustering.ee.infinispan.GroupedKey;
import org.wildfly.clustering.ejb.cache.bean.BeanGroupKey;

/**
 * The key used to cache a group of beans.
 * @author Paul Ferraro
 * @param <K> the bean group identifier type
 */
public class InfinispanBeanGroupKey<K> extends GroupedKey<K> implements BeanGroupKey<K> {

    public InfinispanBeanGroupKey(K id) {
        super(id);
    }
}
