/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.ejb.pool.override;

import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;

import org.jboss.ejb3.annotation.Pool;

/**
 * @author Jaikiran Pai
 */
@Stateless
@LocalBean
@Pool(value = "${something:${poolprefix}123}pool")
public class PoolAnnotatedWithExpressionEJB extends AbstractSlowBean {

    public static final String POOL_NAME = "awesome123pool";
}
