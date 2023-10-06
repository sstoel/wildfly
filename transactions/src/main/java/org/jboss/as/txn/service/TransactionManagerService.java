/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.txn.service;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.tm.usertx.UserTransactionRegistry;
import org.wildfly.security.manager.WildFlySecurityManager;
import org.wildfly.transaction.client.AbstractTransaction;
import org.wildfly.transaction.client.AssociationListener;
import org.wildfly.transaction.client.ContextTransactionManager;
import org.wildfly.transaction.client.CreationListener;
import org.wildfly.transaction.client.LocalTransactionContext;

import jakarta.transaction.TransactionManager;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service responsible for getting the {@link TransactionManager}.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 29-Oct-2010
 */
public class TransactionManagerService implements Service<TransactionManager> {

    /** @deprecated Use the "org.wildfly.transactions.global-default-local-provider" capability to confirm existence of a local provider
     *              and org.wildfly.transaction.client.ContextTransactionManager to obtain a TransactionManager reference. */
    @Deprecated
    public static final ServiceName SERVICE_NAME = TxnServices.JBOSS_TXN_TRANSACTION_MANAGER;
    /** Non-deprecated service name only for use within the subsystem */
    @SuppressWarnings("deprecation")
    public static final ServiceName INTERNAL_SERVICE_NAME = TxnServices.JBOSS_TXN_TRANSACTION_MANAGER;

    private InjectedValue<UserTransactionRegistry> registryInjector = new InjectedValue<>();

    private TransactionManagerService() {
    }

    public static ServiceController<TransactionManager> addService(final ServiceTarget target) {
        final TransactionManagerService service = new TransactionManagerService();
        ServiceBuilder<TransactionManager> serviceBuilder = target.addService(INTERNAL_SERVICE_NAME, service);
        // This is really a dependency on the global context.  TODO: Break this later; no service is needed for TM really
        serviceBuilder.requires(TxnServices.JBOSS_TXN_LOCAL_TRANSACTION_CONTEXT);
        serviceBuilder.addDependency(UserTransactionRegistryService.SERVICE_NAME, UserTransactionRegistry.class, service.registryInjector);
        return serviceBuilder.install();
    }

    public void start(final StartContext context) throws StartException {
        final UserTransactionRegistry registry = registryInjector.getValue();

        LocalTransactionContext.getCurrent().registerCreationListener((txn, createdBy) -> {
            if (createdBy == CreationListener.CreatedBy.USER_TRANSACTION) {
                if (WildFlySecurityManager.isChecking()) {
                    AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
                        txn.registerAssociationListener(new AssociationListener() {
                            private final AtomicBoolean first = new AtomicBoolean();

                            public void associationChanged(final AbstractTransaction t, final boolean a) {
                                if (a && first.compareAndSet(false, true)) registry.userTransactionStarted();
                            }
                        });
                        return null;
                    });
                } else {
                    txn.registerAssociationListener(new AssociationListener() {
                        private final AtomicBoolean first = new AtomicBoolean();

                        public void associationChanged(final AbstractTransaction t, final boolean a) {
                            if (a && first.compareAndSet(false, true)) registry.userTransactionStarted();
                        }
                    });
                }
            }
        });
    }

    @Override
    public void stop(final StopContext stopContext) {
        // noop
    }

    @Override
    public TransactionManager getValue() throws IllegalStateException {
        return ContextTransactionManager.getInstance();
    }
}
