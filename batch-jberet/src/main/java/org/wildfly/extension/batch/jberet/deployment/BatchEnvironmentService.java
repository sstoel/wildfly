/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.batch.jberet.deployment;

import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Supplier;
import jakarta.transaction.TransactionManager;

import org.jberet.repository.JobRepository;
import org.jberet.spi.ArtifactFactory;
import org.jberet.spi.BatchEnvironment;
import org.jberet.spi.JobExecutor;
import org.jberet.spi.JobTask;
import org.jberet.spi.JobXmlResolver;
import org.jboss.as.naming.context.NamespaceContextSelector;
import org.jboss.logging.MDC;
import org.jboss.logging.NDC;
import org.jboss.msc.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.extension.batch.jberet.BatchConfiguration;
import org.wildfly.extension.batch.jberet._private.BatchLogger;
import org.wildfly.extension.requestcontroller.ControlPoint;
import org.wildfly.extension.requestcontroller.RequestController;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.wildfly.security.manager.WildFlySecurityManager;
import org.wildfly.transaction.client.ContextTransactionManager;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class BatchEnvironmentService implements Service {

    private static final Properties PROPS = new Properties();
    private final Consumer<SecurityAwareBatchEnvironment> batchEnvironmentConsumer;
    private final Supplier<WildFlyArtifactFactory> artifactFactorySupplier;
    private final Supplier<JobExecutor> jobExecutorSupplier;
    private final Supplier<RequestController> requestControllerSupplier;
    private final Supplier<JobRepository> jobRepositorySupplier;
    private final Supplier<BatchConfiguration> batchConfigurationSupplier;

    private final ClassLoader classLoader;
    private final JobXmlResolver jobXmlResolver;
    private final String deploymentName;
    private final NamespaceContextSelector namespaceContextSelector;
    private SecurityAwareBatchEnvironment batchEnvironment = null;
    private volatile ControlPoint controlPoint;

    public BatchEnvironmentService(final Consumer<SecurityAwareBatchEnvironment> batchEnvironmentConsumer,
                                   final Supplier<WildFlyArtifactFactory> artifactFactorySupplier,
                                   final Supplier<JobExecutor> jobExecutorSupplier,
                                   final Supplier<RequestController> requestControllerSupplier,
                                   final Supplier<JobRepository> jobRepositorySupplier,
                                   final Supplier<BatchConfiguration> batchConfigurationSupplier,
                                   final ClassLoader classLoader,
                                   final JobXmlResolver jobXmlResolver,
                                   final String deploymentName,
                                   final NamespaceContextSelector namespaceContextSelector) {
        this.batchEnvironmentConsumer = batchEnvironmentConsumer;
        this.artifactFactorySupplier = artifactFactorySupplier;
        this.jobExecutorSupplier = jobExecutorSupplier;
        this.requestControllerSupplier = requestControllerSupplier;
        this.jobRepositorySupplier = jobRepositorySupplier;
        this.batchConfigurationSupplier = batchConfigurationSupplier;
        this.classLoader = classLoader;
        this.jobXmlResolver = jobXmlResolver;
        this.deploymentName = deploymentName;
        this.namespaceContextSelector = namespaceContextSelector;
    }

    @Override
    public synchronized void start(final StartContext context) throws StartException {
        BatchLogger.LOGGER.debugf("Creating batch environment; %s", classLoader);
        final BatchConfiguration batchConfiguration = batchConfigurationSupplier.get();
        // Find the job executor to use
        JobExecutor jobExecutor = jobExecutorSupplier != null ? jobExecutorSupplier.get() : null;
        if (jobExecutor == null) {
            jobExecutor = batchConfiguration.getDefaultJobExecutor();
        }
        // Find the job repository to use
        JobRepository jobRepository = jobRepositorySupplier != null ? jobRepositorySupplier.get() : null;
        if (jobRepository == null) {
            jobRepository = batchConfiguration.getDefaultJobRepository();
        }

        this.batchEnvironment = new WildFlyBatchEnvironment(artifactFactorySupplier.get(),
                jobExecutor, ContextTransactionManager.getInstance(),
                jobRepository, jobXmlResolver);

        final RequestController requestController = requestControllerSupplier != null ? requestControllerSupplier.get() : null;
        if (requestController != null) {
            // Create the entry point
            controlPoint = requestController.getControlPoint(deploymentName, "batch-executor-service");
        } else {
            controlPoint = null;
        }
        batchEnvironmentConsumer.accept(batchEnvironment);
    }

    @Override
    public synchronized void stop(final StopContext context) {
        batchEnvironmentConsumer.accept(null);
        BatchLogger.LOGGER.debugf("Removing batch environment; %s", classLoader);
        batchEnvironment = null;
        if (controlPoint != null) {
            requestControllerSupplier.get().removeControlPoint(controlPoint);
        }
    }

    private class WildFlyBatchEnvironment implements BatchEnvironment, SecurityAwareBatchEnvironment {

        private final WildFlyArtifactFactory artifactFactory;
        private final JobExecutor jobExecutor;
        private final TransactionManager transactionManager;
        private final JobRepository jobRepository;
        private final JobXmlResolver jobXmlResolver;

        WildFlyBatchEnvironment(final WildFlyArtifactFactory artifactFactory,
                                final JobExecutor jobExecutor,
                                final TransactionManager transactionManager,
                                final JobRepository jobRepository,
                                final JobXmlResolver jobXmlResolver) {
            this.jobXmlResolver = jobXmlResolver;
            this.artifactFactory = artifactFactory;
            this.jobExecutor = jobExecutor;
            this.transactionManager = transactionManager;
            this.jobRepository = jobRepository;
        }

        @Override
        public ClassLoader getClassLoader() {
            return classLoader;
        }

        @Override
        public ArtifactFactory getArtifactFactory() {
            return artifactFactory;
        }

        @Override
        public void submitTask(final JobTask jobTask) {
            final SecurityIdentity identity = getIdentity();
            final ContextHandle contextHandle = createContextHandle();
            final JobTask task = new JobTask() {
                @Override
                public int getRequiredRemainingPermits() {
                    return jobTask.getRequiredRemainingPermits();
                }

                @Override
                public void run() {
                    final ContextHandle.Handle handle = contextHandle.setup();
                    try {
                        if (identity == null) {
                            jobTask.run();
                        } else {
                            identity.runAs(jobTask);
                        }
                    } finally {
                        handle.tearDown();
                    }
                }
            };
            if (controlPoint == null) {
                jobExecutor.execute(task);
            } else {
                // Queue the task to run in the control point, if resume is executed the queued tasks will run
                controlPoint.queueTask(task, jobExecutor, -1, null, false);
            }
        }

        @Override
        public TransactionManager getTransactionManager() {
            return transactionManager;
        }

        @Override
        public JobRepository getJobRepository() {
            return jobRepository;
        }

        @Override
        public JobXmlResolver getJobXmlResolver() {
            return jobXmlResolver;
        }

        @Override
        public Properties getBatchConfigurationProperties() {
            return PROPS;
        }

        @Override
        public String getApplicationName() {
            return deploymentName;
        }

        @Override
        public SecurityDomain getSecurityDomain() {
            return batchConfigurationSupplier.get().getSecurityDomain();
        }

        private ContextHandle createContextHandle() {
            final ClassLoader tccl = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
            // If the TCCL is null, use the deployments ModuleClassLoader
            final ClassLoaderContextHandle classLoaderContextHandle = (tccl == null ? new ClassLoaderContextHandle(classLoader) : new ClassLoaderContextHandle(tccl));
            // Class loader handle must be first so the TCCL is set before the other handles execute
            return new ContextHandle.ChainedContextHandle(classLoaderContextHandle, new NamespaceContextHandle(namespaceContextSelector),
                     artifactFactory.createContextHandle(), new ConcurrentContextHandle(),
                    new DiagnosticContextHandle(MDC.getMap(), NDC.get()));
        }
    }
}
