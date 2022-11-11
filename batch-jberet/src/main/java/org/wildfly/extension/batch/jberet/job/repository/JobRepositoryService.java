/*
 * Copyright 2015 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.batch.jberet.job.repository;

import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import jakarta.batch.runtime.JobExecution;
import jakarta.batch.runtime.JobInstance;
import jakarta.batch.runtime.StepExecution;

import org.jberet.job.model.Job;
import org.jberet.repository.ApplicationAndJobName;
import org.jberet.repository.JobExecutionSelector;
import org.jberet.repository.JobRepository;
import org.jberet.runtime.AbstractStepExecution;
import org.jberet.runtime.JobExecutionImpl;
import org.jberet.runtime.JobInstanceImpl;
import org.jberet.runtime.PartitionExecutionImpl;
import org.jberet.runtime.StepExecutionImpl;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.extension.batch.jberet._private.BatchLogger;

/**
 * An abstract service which delegates to a {@link JobRepository} throwing an {@link IllegalStateException} if the
 * service has been stopped.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
abstract class JobRepositoryService implements JobRepository, Service<JobRepository> {
    private volatile boolean started;
    private final Integer executionRecordsLimit;
    private final Consumer<JobRepository> jobRepositoryConsumer;

    public JobRepositoryService(final Consumer<JobRepository> jobRepositoryConsumer, final Integer executionRecordsLimit) {
        this.jobRepositoryConsumer = jobRepositoryConsumer;
        this.executionRecordsLimit = executionRecordsLimit;
    }

    @Override
    public final void start(final StartContext context) throws StartException {
        startJobRepository(context);
        started = true;
        jobRepositoryConsumer.accept(this);
    }

    @Override
    public final void stop(final StopContext context) {
        jobRepositoryConsumer.accept(null);
        stopJobRepository(context);
        started = false;
    }

    @Override
    public JobRepository getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    @Override
    public void addJob(final ApplicationAndJobName applicationAndJobName, final Job job) {
        getAndCheckDelegate().addJob(applicationAndJobName, job);
    }

    @Override
    public void removeJob(final String jobId) {
        getAndCheckDelegate().removeJob(jobId);
    }

    @Override
    public Job getJob(final ApplicationAndJobName applicationAndJobName) {
        return getAndCheckDelegate().getJob(applicationAndJobName);
    }

    @Override
    public Set<String> getJobNames() {
        return getAndCheckDelegate().getJobNames();
    }

    /**
     * {@inheritDoc}
     * <p>
     * WildFly JBeret subsystem validates a job name before each batch job operations.
     * If a job name is invalid, {@code NoSuchJobException} would already have been thrown.
     * So this method is optimized to always return true.
     */
    @Override
    public boolean jobExists(final String jobName) {
        return true;
    }

    @Override
    public JobInstanceImpl createJobInstance(final Job job, final String applicationName, final ClassLoader classLoader) {
        return getAndCheckDelegate().createJobInstance(job, applicationName, classLoader);
    }

    @Override
    public void removeJobInstance(final long jobInstanceId) {
        getAndCheckDelegate().removeJobInstance(jobInstanceId);
    }

    @Override
    public JobInstance getJobInstance(final long jobInstanceId) {
        return getAndCheckDelegate().getJobInstance(jobInstanceId);
    }

    @Override
    public List<JobInstance> getJobInstances(final String jobName) {
        return getAndCheckDelegate().getJobInstances(jobName);
    }

    @Override
    public int getJobInstanceCount(final String jobName) {
        return getAndCheckDelegate().getJobInstanceCount(jobName);
    }

    @Override
    public JobExecutionImpl createJobExecution(final JobInstanceImpl jobInstance, final Properties jobParameters) {
        return getAndCheckDelegate().createJobExecution(jobInstance, jobParameters);
    }

    @Override
    public JobExecution getJobExecution(final long jobExecutionId) {
        return getAndCheckDelegate().getJobExecution(jobExecutionId);
    }

    @Override
    public List<JobExecution> getJobExecutions(final JobInstance jobInstance) {
        return getAndCheckDelegate().getJobExecutions(jobInstance);
    }

    @Override
    public void updateJobExecution(final JobExecutionImpl jobExecution, final boolean fullUpdate, final boolean saveJobParameters) {
        getAndCheckDelegate().updateJobExecution(jobExecution, fullUpdate, saveJobParameters);
    }

    @Override
    public void stopJobExecution(final JobExecutionImpl jobExecution) {
        getAndCheckDelegate().stopJobExecution(jobExecution);
    }

    @Override
    public List<Long> getRunningExecutions(final String jobName) {
        return getAndCheckDelegate().getRunningExecutions(jobName);
    }

    @Override
    public void removeJobExecutions(final JobExecutionSelector jobExecutionSelector) {
        getAndCheckDelegate().removeJobExecutions(jobExecutionSelector);
    }

    @Override
    public List<StepExecution> getStepExecutions(final long jobExecutionId, final ClassLoader classLoader) {
        return getAndCheckDelegate().getStepExecutions(jobExecutionId, classLoader);
    }

    @Override
    public StepExecutionImpl createStepExecution(final String stepName) {
        return getAndCheckDelegate().createStepExecution(stepName);
    }

    @Override
    public void addStepExecution(final JobExecutionImpl jobExecution, final StepExecutionImpl stepExecution) {
        getAndCheckDelegate().addStepExecution(jobExecution, stepExecution);
    }

    @Override
    public void updateStepExecution(final StepExecution stepExecution) {
        getAndCheckDelegate().updateStepExecution(stepExecution);
    }

    @Override
    public StepExecutionImpl findOriginalStepExecutionForRestart(final String stepName, final JobExecutionImpl jobExecutionToRestart, final ClassLoader classLoader) {
        return getAndCheckDelegate().findOriginalStepExecutionForRestart(stepName, jobExecutionToRestart, classLoader);
    }

    @Override
    public int countStepStartTimes(final String stepName, final long jobInstanceId) {
        return getAndCheckDelegate().countStepStartTimes(stepName, jobInstanceId);
    }

    @Override
    public void addPartitionExecution(final StepExecutionImpl enclosingStepExecution, final PartitionExecutionImpl partitionExecution) {
        getAndCheckDelegate().addPartitionExecution(enclosingStepExecution, partitionExecution);
    }

    @Override
    public List<PartitionExecutionImpl> getPartitionExecutions(final long stepExecutionId, final StepExecutionImpl stepExecution, final boolean notCompletedOnly, final ClassLoader classLoader) {
        return getAndCheckDelegate().getPartitionExecutions(stepExecutionId, stepExecution, notCompletedOnly, classLoader);
    }

    @Override
    public void savePersistentData(final JobExecution jobExecution, final AbstractStepExecution stepOrPartitionExecution) {
        getAndCheckDelegate().savePersistentData(jobExecution, stepOrPartitionExecution);
    }

    @Override
    public int savePersistentDataIfNotStopping(final JobExecution jobExecution, final AbstractStepExecution abstractStepExecution) {
        return getAndCheckDelegate().savePersistentDataIfNotStopping(jobExecution, abstractStepExecution);
    }

    @Override
    public List<Long> getJobExecutionsByJob(final String jobName) {
        return getAndCheckDelegate().getJobExecutionsByJob(jobName, executionRecordsLimit);
    }

    @Override
    public List<Long> getJobExecutionsByJob(String jobName, Integer executionRecordsLimit) {
        return getAndCheckDelegate().getJobExecutionsByJob(jobName, executionRecordsLimit);
    }

    protected abstract void startJobRepository(StartContext context) throws StartException;

    protected abstract void stopJobRepository(StopContext context);

    protected abstract JobRepository getDelegate();

    private JobRepository getAndCheckDelegate() {
        final JobRepository delegate = getDelegate();
        if (started && delegate != null) {
            return delegate;
        }
        throw BatchLogger.LOGGER.jobOperatorServiceStopped();
    }
}
