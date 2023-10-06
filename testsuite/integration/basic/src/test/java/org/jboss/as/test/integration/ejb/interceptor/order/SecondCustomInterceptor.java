/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.ejb.interceptor.order;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class SecondCustomInterceptor extends AbstractCustomInterceptor {
    public SecondCustomInterceptor() {
        super("Second");
    }

}
