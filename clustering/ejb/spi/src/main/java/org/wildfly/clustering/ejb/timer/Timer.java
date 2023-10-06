/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.ejb.timer;

/**
 * Describes the properties of a timer, and its controlling mechanisms.
 * @author Paul Ferraro
 * @param <I> the timer identifier type
 */
public interface Timer<I> {

    I getId();
    ImmutableTimerMetaData getMetaData();
    boolean isActive();
    boolean isCanceled();

    void cancel();

    void invoke() throws Exception;

    void activate();
    void suspend();
}
