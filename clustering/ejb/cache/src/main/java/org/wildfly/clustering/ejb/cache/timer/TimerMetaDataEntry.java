/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.ejb.cache.timer;

import java.time.Duration;

/**
 * @author Paul Ferraro
 */
public interface TimerMetaDataEntry<C> extends ImmutableTimerMetaDataEntry<C> {

    void setLastTimeout(Duration timeout);
}
