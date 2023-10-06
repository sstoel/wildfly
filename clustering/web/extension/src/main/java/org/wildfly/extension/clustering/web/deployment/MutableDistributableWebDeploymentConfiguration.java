/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.clustering.web.deployment;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import org.jboss.as.ee.structure.JBossDescriptorPropertyReplacement;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.metadata.property.PropertyReplacer;
import org.wildfly.clustering.web.service.session.DistributableSessionManagementProvider;
import org.wildfly.clustering.web.session.DistributableSessionManagementConfiguration;

/**
 * @author Paul Ferraro
 */
public class MutableDistributableWebDeploymentConfiguration implements DistributableWebDeploymentConfiguration, UnaryOperator<String>, Consumer<String> {

    private final List<String> immutableClasses = new LinkedList<>();
    private final PropertyReplacer replacer;

    private String managementName;
    private DistributableSessionManagementProvider<? extends DistributableSessionManagementConfiguration<DeploymentUnit>> management;

    public MutableDistributableWebDeploymentConfiguration(PropertyReplacer replacer) {
        this.replacer = replacer;
    }

    public MutableDistributableWebDeploymentConfiguration(DeploymentUnit unit) {
        this(JBossDescriptorPropertyReplacement.propertyReplacer(unit));
    }

    @Override
    public DistributableSessionManagementProvider<? extends DistributableSessionManagementConfiguration<DeploymentUnit>> getSessionManagement() {
        return this.management;
    }

    public void setSessionManagement(DistributableSessionManagementProvider<? extends DistributableSessionManagementConfiguration<DeploymentUnit>> management) {
        this.management = management;
    }

    @Override
    public String getSessionManagementName() {
        return this.managementName;
    }

    public void setSessionManagementName(String value) {
        this.managementName = this.apply(value);
    }

    @Override
    public List<String> getImmutableClasses() {
        return Collections.unmodifiableList(this.immutableClasses);
    }

    @Override
    public void accept(String className) {
        this.immutableClasses.add(className);
    }

    @Override
    public String apply(String value) {
        return this.replacer.replaceProperties(value);
    }
}
