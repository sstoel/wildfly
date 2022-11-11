/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.wildfly.extension.batch.jberet.deployment;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Properties;
import java.util.function.Function;
import jakarta.batch.operations.JobExecutionAlreadyCompleteException;
import jakarta.batch.operations.JobExecutionNotMostRecentException;
import jakarta.batch.operations.JobExecutionNotRunningException;
import jakarta.batch.operations.JobRestartException;
import jakarta.batch.operations.JobSecurityException;
import jakarta.batch.operations.NoSuchJobExecutionException;
import jakarta.batch.runtime.BatchStatus;
import jakarta.batch.runtime.JobExecution;
import jakarta.batch.runtime.JobInstance;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleMapAttributeDefinition;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.extension.batch.jberet.BatchResourceDescriptionResolver;

/**
 * A definition representing a job execution resource.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class BatchJobExecutionResourceDefinition extends SimpleResourceDefinition {
    static final String EXECUTION = "execution";

    static final SimpleAttributeDefinition INSTANCE_ID = SimpleAttributeDefinitionBuilder.create("instance-id", ModelType.LONG)
            .setStorageRuntime()
            .build();

    static final SimpleAttributeDefinition BATCH_STATUS = SimpleAttributeDefinitionBuilder.create("batch-status", ModelType.STRING)
            .setStorageRuntime()
            .build();

    static final SimpleAttributeDefinition EXIT_STATUS = SimpleAttributeDefinitionBuilder.create("exit-status", ModelType.STRING)
            .setStorageRuntime()
            .build();

    static final SimpleAttributeDefinition CREATE_TIME = SimpleAttributeDefinitionBuilder.create("create-time", ModelType.STRING)
            .setStorageRuntime()
            .build();

    static final SimpleAttributeDefinition START_TIME = SimpleAttributeDefinitionBuilder.create("start-time", ModelType.STRING)
            .setStorageRuntime()
            .build();

    static final SimpleAttributeDefinition LAST_UPDATED_TIME = SimpleAttributeDefinitionBuilder.create("last-updated-time", ModelType.STRING)
            .setStorageRuntime()
            .build();

    static final SimpleAttributeDefinition END_TIME = SimpleAttributeDefinitionBuilder.create("end-time", ModelType.STRING)
            .setStorageRuntime()
            .build();

    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.systemDefault();

    private static final ResourceDescriptionResolver DEFAULT_RESOLVER = BatchResourceDescriptionResolver.getResourceDescriptionResolver("deployment", "job", "execution");

    private static final SimpleMapAttributeDefinition PROPERTIES = new SimpleMapAttributeDefinition.Builder("properties", ModelType.STRING, true)
            .build();

    private static final SimpleOperationDefinition RESTART_JOB = new SimpleOperationDefinitionBuilder("restart-job", DEFAULT_RESOLVER)
            .setParameters(PROPERTIES)
            .setReplyType(ModelType.LONG)
            .setRuntimeOnly()
            .build();

    private static final SimpleOperationDefinition STOP_JOB = new SimpleOperationDefinitionBuilder("stop-job", DEFAULT_RESOLVER)
            .setRuntimeOnly()
            .build();

    public BatchJobExecutionResourceDefinition() {
        super(new Parameters(PathElement.pathElement(EXECUTION), DEFAULT_RESOLVER).setRuntime());
    }

    @Override
    public void registerAttributes(final ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadOnlyAttribute(INSTANCE_ID, new JobOperationReadOnlyStepHandler() {
            @Override
            protected void updateModel(final OperationContext context, final ModelNode model, final WildFlyJobOperator jobOperator, final String jobName) throws OperationFailedException {
                final JobInstance jobInstance = jobOperator.getJobInstance(Long.parseLong(context.getCurrentAddressValue()));
                model.set(jobInstance.getInstanceId());
            }
        });
        resourceRegistration.registerReadOnlyAttribute(BATCH_STATUS, new JobExecutionOperationStepHandler() {
            @Override
            protected void updateModel(final ModelNode model, final JobExecution jobExecution) throws OperationFailedException {
                final BatchStatus status = jobExecution.getBatchStatus();
                if (status != null) {
                    model.set(status.toString());
                }
            }
        });
        resourceRegistration.registerReadOnlyAttribute(EXIT_STATUS, new JobExecutionOperationStepHandler() {
            @Override
            protected void updateModel(final ModelNode model, final JobExecution jobExecution) throws OperationFailedException {
                final String exitStatus = jobExecution.getExitStatus();
                if (exitStatus != null) {
                    model.set(exitStatus);
                }
            }
        });
        resourceRegistration.registerReadOnlyAttribute(CREATE_TIME, new DateTimeFormatterOperationStepHandler(JobExecution::getCreateTime));
        resourceRegistration.registerReadOnlyAttribute(START_TIME, new DateTimeFormatterOperationStepHandler(JobExecution::getStartTime));
        resourceRegistration.registerReadOnlyAttribute(LAST_UPDATED_TIME, new DateTimeFormatterOperationStepHandler(JobExecution::getLastUpdatedTime));
        resourceRegistration.registerReadOnlyAttribute(END_TIME, new DateTimeFormatterOperationStepHandler(JobExecution::getEndTime));
    }

    @Override
    public void registerOperations(final ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);

        resourceRegistration.registerOperationHandler(STOP_JOB, new JobOperationStepHandler() {
            @Override
            protected void execute(final OperationContext context, final ModelNode operation, final WildFlyJobOperator jobOperator) throws OperationFailedException {
                // Resolve the execution id
                final long executionId = Long.parseLong(context.getCurrentAddressValue());
                try {
                    jobOperator.stop(executionId);
                } catch (NoSuchJobExecutionException | JobExecutionNotRunningException | JobSecurityException e) {
                    throw createOperationFailure(e);
                }
            }
        });

        resourceRegistration.registerOperationHandler(RESTART_JOB, new JobOperationStepHandler() {
            @Override
            protected void execute(final OperationContext context, final ModelNode operation, final WildFlyJobOperator jobOperator) throws OperationFailedException {
                // Resolve the execution id
                final long executionId = Long.parseLong(context.getCurrentAddressValue());
                // Get the properties
                final Properties properties = resolvePropertyValue(context, operation, PROPERTIES);
                try {
                    final long newExecutionId = jobOperator.restart(executionId, properties);
                    context.getResult().set(newExecutionId);
                } catch (JobExecutionAlreadyCompleteException | NoSuchJobExecutionException | JobExecutionNotMostRecentException | JobRestartException | JobSecurityException e) {
                    throw createOperationFailure(e);
                }
            }
        });
    }

    abstract static class JobExecutionOperationStepHandler extends JobOperationReadOnlyStepHandler {
        @Override
        protected void updateModel(final OperationContext context, final ModelNode model, final WildFlyJobOperator jobOperator, final String jobName) throws OperationFailedException {
            final JobExecution jobExecution = jobOperator.getJobExecution(Long.parseLong(context.getCurrentAddressValue()));
            updateModel(model, jobExecution);
        }

        protected abstract void updateModel(ModelNode model, JobExecution jobExecution) throws OperationFailedException;
    }

    static class DateTimeFormatterOperationStepHandler extends JobExecutionOperationStepHandler {

        private final Function<JobExecution, Date> dateGetter;

        public DateTimeFormatterOperationStepHandler(final Function<JobExecution, Date> dateGetter) {
            this.dateGetter = dateGetter;
        }

        protected void updateModel(final ModelNode model, final JobExecution jobExecution) throws OperationFailedException {
            final Date date = dateGetter.apply(jobExecution);
            if (date != null) {
                // use OffsetDateTime and ISO_OFFSET_DATE_TIME if we want to include offset in the formatting output
                model.set(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), DEFAULT_ZONE_ID)));
            }
        }
    }
}
