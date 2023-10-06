/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ejb3.deployment.processors.merging;

import java.lang.reflect.Method;

import jakarta.ejb.SessionBean;

import org.jboss.as.ee.component.ComponentConfiguration;
import org.jboss.as.ee.component.ComponentConfigurator;
import org.jboss.as.ee.component.ComponentDescription;
import org.jboss.as.ee.component.EEApplicationClasses;
import org.jboss.as.ee.component.interceptors.InterceptorClassDescription;
import org.jboss.as.ee.component.interceptors.InterceptorOrder;
import org.jboss.as.ejb3.component.session.SessionBeanComponentDescription;
import org.jboss.as.ejb3.component.session.SessionBeanSetSessionContextMethodInvocationInterceptor;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.reflect.ClassReflectionIndex;
import org.jboss.as.server.deployment.reflect.DeploymentReflectionIndex;
import org.jboss.invocation.proxy.MethodIdentifier;

/**
 * Processor that handles the {@link jakarta.ejb.SessionBean} interface
 *
 * @author Stuart Douglas
 */
public class SessionBeanMergingProcessor extends AbstractMergingProcessor<SessionBeanComponentDescription> {

    public SessionBeanMergingProcessor() {
        super(SessionBeanComponentDescription.class);
    }

    @Override
    protected void handleAnnotations(final DeploymentUnit deploymentUnit, final EEApplicationClasses applicationClasses, final DeploymentReflectionIndex deploymentReflectionIndex, final Class<?> componentClass, final SessionBeanComponentDescription description) throws DeploymentUnitProcessingException {

    }

    @Override
    protected void handleDeploymentDescriptor(final DeploymentUnit deploymentUnit, final DeploymentReflectionIndex deploymentReflectionIndex, final Class<?> componentClass, final SessionBeanComponentDescription description) throws DeploymentUnitProcessingException {
        if (SessionBean.class.isAssignableFrom(componentClass)) {
            // add the setSessionContext(SessionContext) method invocation interceptor for session bean implementing the jakarta.ejb.SessionContext
            // interface
            description.getConfigurators().add(new ComponentConfigurator() {
                @Override
                public void configure(DeploymentPhaseContext context, ComponentDescription description, ComponentConfiguration configuration) throws DeploymentUnitProcessingException {
                    if (SessionBean.class.isAssignableFrom(configuration.getComponentClass())) {
                        configuration.addPostConstructInterceptor(SessionBeanSetSessionContextMethodInvocationInterceptor.FACTORY, InterceptorOrder.ComponentPostConstruct.EJB_SET_CONTEXT_METHOD_INVOCATION_INTERCEPTOR);
                    }
                }
            });

            //now lifecycle callbacks
            final MethodIdentifier ejbRemoveIdentifier = MethodIdentifier.getIdentifier(void.class, "ejbRemove");
            final MethodIdentifier ejbActivateIdentifier = MethodIdentifier.getIdentifier(void.class, "ejbActivate");
            final MethodIdentifier ejbPassivateIdentifier = MethodIdentifier.getIdentifier(void.class, "ejbPassivate");

            boolean ejbActivate = false, ejbPassivate = false, ejbRemove = false;
            Class<?> c  = componentClass;
            while (c != null && c != Object.class) {
                final ClassReflectionIndex index = deploymentReflectionIndex.getClassIndex(c);

                if(!ejbActivate) {
                    final Method method = index.getMethod(ejbActivateIdentifier);
                    if(method != null) {
                        final InterceptorClassDescription.Builder builder = InterceptorClassDescription.builder();
                        builder.setPostActivate(ejbActivateIdentifier);
                        description.addInterceptorMethodOverride(c.getName(), builder.build());
                        ejbActivate = true;
                    }
                }

                if(!ejbPassivate) {
                    final Method method = index.getMethod(ejbPassivateIdentifier);
                    if(method != null) {
                        final InterceptorClassDescription.Builder builder = InterceptorClassDescription.builder();
                        builder.setPrePassivate(ejbPassivateIdentifier);
                        description.addInterceptorMethodOverride(c.getName(), builder.build());
                        ejbPassivate = true;
                    }
                }

                if(!ejbRemove) {
                    final Method method = index.getMethod(ejbRemoveIdentifier);
                    if(method != null) {
                        final InterceptorClassDescription.Builder builder = InterceptorClassDescription.builder();
                        builder.setPreDestroy(ejbRemoveIdentifier);
                        description.addInterceptorMethodOverride(c.getName(), builder.build());
                        ejbRemove = true;
                    }
                }

                c = c.getSuperclass();
            }


        }
    }
}
