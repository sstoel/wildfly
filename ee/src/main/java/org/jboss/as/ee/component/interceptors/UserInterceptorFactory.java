/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ee.component.interceptors;

/**
 * Interceptor factory that handles user interceptors, and switches between timer normal invocations
 *
 * @author Stuart Douglas
 */

import org.jboss.invocation.Interceptor;
import org.jboss.invocation.InterceptorContext;
import org.jboss.invocation.InterceptorFactory;
import org.jboss.invocation.InterceptorFactoryContext;


public class UserInterceptorFactory implements InterceptorFactory {
    private final InterceptorFactory aroundInvoke;
    private final InterceptorFactory aroundTimeout;

    public UserInterceptorFactory(final InterceptorFactory aroundInvoke, final InterceptorFactory aroundTimeout) {
        this.aroundInvoke = aroundInvoke;
        this.aroundTimeout = aroundTimeout;
    }


    @Override
    public Interceptor create(final InterceptorFactoryContext context) {
        final Interceptor aroundInvoke = this.aroundInvoke.create(context);
        final Interceptor aroundTimeout;
        if(this.aroundTimeout != null) {
            aroundTimeout = this.aroundTimeout.create(context);
        } else {
            aroundTimeout = null;
        }
        return new Interceptor() {
            @Override
            public Object processInvocation(final InterceptorContext context) throws Exception {
                final InvocationType marker = context.getPrivateData(InvocationType.class);
                if (marker == InvocationType.TIMER) {
                    return aroundTimeout.processInvocation(context);
                } else {
                    return aroundInvoke.processInvocation(context);
                }
            }
        };

    }
}
