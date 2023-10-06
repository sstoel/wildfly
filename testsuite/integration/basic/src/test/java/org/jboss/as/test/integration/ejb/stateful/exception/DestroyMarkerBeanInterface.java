/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.ejb.stateful.exception;

public interface DestroyMarkerBeanInterface {
    boolean is();
    void set(boolean preDestroy);
}
