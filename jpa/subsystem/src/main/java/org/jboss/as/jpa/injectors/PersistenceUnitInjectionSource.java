/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.jpa.injectors;


import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import jakarta.persistence.EntityManagerFactory;

import org.jboss.as.ee.component.InjectionSource;
import org.jboss.as.jpa.messages.JpaLogger;
import org.jboss.as.jpa.service.PersistenceUnitServiceImpl;
import org.jboss.as.naming.ManagedReference;
import org.jboss.as.naming.ManagedReferenceFactory;
import org.jboss.as.naming.ValueManagedReference;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jipijapa.plugin.spi.PersistenceUnitMetadata;

/**
 * Represents the PersistenceUnit injected into a component.
 * TODO:  support injecting into a HibernateSessionFactory.  Initially, hack it by checking injectionTypeName parameter
 * for HibernateSessionFactory.  If/when Jakarta Persistence supports unwrap on the EMF, switch to that.
 *
 * @author Scott Marlow
 */
public class PersistenceUnitInjectionSource extends InjectionSource {

    private final PersistenceUnitJndiInjectable injectable;
    private final ServiceName puServiceName;

    public PersistenceUnitInjectionSource(final ServiceName puServiceName, final ServiceRegistry serviceRegistry, final String injectionTypeName, final PersistenceUnitMetadata pu) {

        injectable = new PersistenceUnitJndiInjectable(puServiceName, serviceRegistry, injectionTypeName, pu);
        this.puServiceName = puServiceName;
    }

    public void getResourceValue(final ResolutionContext resolutionContext, final ServiceBuilder<?> serviceBuilder, final DeploymentPhaseContext phaseContext, final Injector<ManagedReferenceFactory> injector) throws
        DeploymentUnitProcessingException {
        serviceBuilder.requires(puServiceName);
        injector.inject(injectable);
    }

    public boolean equals(final Object other) {
        if (other instanceof PersistenceUnitInjectionSource) {
            PersistenceUnitInjectionSource source = (PersistenceUnitInjectionSource) other;
            return (source.puServiceName.equals(puServiceName));
        }
        return false;
    }

    public int hashCode() {
        return puServiceName.hashCode();
    }

    private static final class PersistenceUnitJndiInjectable implements ManagedReferenceFactory {

        final ServiceName puServiceName;
        final ServiceRegistry serviceRegistry;
        final String injectionTypeName;
        final PersistenceUnitMetadata pu;
        private static final String ENTITY_MANAGER_FACTORY_CLASS = "jakarta.persistence.EntityManagerFactory";

        public PersistenceUnitJndiInjectable(
            final ServiceName puServiceName,
            final ServiceRegistry serviceRegistry,
            final String injectionTypeName,
            final PersistenceUnitMetadata pu) {

            this.puServiceName = puServiceName;
            this.serviceRegistry = serviceRegistry;
            this.injectionTypeName = injectionTypeName;
            this.pu = pu;
        }

        @Override
        public ManagedReference getReference() {
            PersistenceUnitServiceImpl service = (PersistenceUnitServiceImpl) serviceRegistry.getRequiredService(puServiceName).getValue();
            EntityManagerFactory emf = service.getEntityManagerFactory();

            if (!ENTITY_MANAGER_FACTORY_CLASS.equals(injectionTypeName)) { // inject non-standard wrapped class (e.g. org.hibernate.SessionFactory)
                Class<?> extensionClass;
                try {
                    // make sure we can access the target class type
                    extensionClass = pu.getClassLoader().loadClass(injectionTypeName);
                } catch (ClassNotFoundException e) {
                    throw JpaLogger.ROOT_LOGGER.cannotLoadFromJpa(e, injectionTypeName);
                }
                // TODO:  when/if Jakarta Persistence supports unwrap, change to
                //   Object targetValueToInject = emf.unwrap(extensionClass);
                // Until Jakarta Persistence supports unwrap on sessionfactory, only support hibernate

                Method getSessionFactory;
                try {
                    getSessionFactory = emf.getClass().getMethod("getSessionFactory");
                } catch (NoSuchMethodException e) {
                    throw JpaLogger.ROOT_LOGGER.hibernateOnlyEntityManagerFactory();
                }

                Object targetValueToInject = null;
                try {
                    targetValueToInject = getSessionFactory.invoke(emf, new Object[0]);
                } catch (IllegalAccessException e) {
                    throw JpaLogger.ROOT_LOGGER.cannotGetSessionFactory(e);
                } catch (InvocationTargetException e) {
                    throw JpaLogger.ROOT_LOGGER.cannotGetSessionFactory(e);
                }
                return new ValueManagedReference(targetValueToInject);
            }

            return new ValueManagedReference(emf);
        }
    }

}
