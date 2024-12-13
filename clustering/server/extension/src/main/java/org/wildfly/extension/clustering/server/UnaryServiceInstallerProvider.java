/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.clustering.server;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.jboss.as.clustering.naming.BinderServiceInstaller;
import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.naming.deployment.ContextNames;
import org.jboss.as.naming.deployment.JndiName;
import org.jboss.msc.service.ServiceName;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;
import org.wildfly.subsystem.service.ServiceInstaller;

/**
 * @author Paul Ferraro
 */
public class UnaryServiceInstallerProvider<T> implements BiFunction<CapabilityServiceSupport, String, Iterable<ServiceInstaller>> {

    private final UnaryServiceDescriptor<T> descriptor;
    private final BiFunction<CapabilityServiceSupport, String, ServiceInstaller> installerFactory;
    private final Function<String, JndiName> jndiNameFactory;

    protected UnaryServiceInstallerProvider(UnaryServiceDescriptor<T> descriptor, BiFunction<CapabilityServiceSupport, String, ServiceInstaller> installerFactory) {
        this(descriptor, installerFactory, null);
    }

    protected UnaryServiceInstallerProvider(UnaryServiceDescriptor<T> descriptor, BiFunction<CapabilityServiceSupport, String, ServiceInstaller> installerFactory, Function<String, JndiName> jndiNameFactory) {
        this.descriptor = descriptor;
        this.installerFactory = installerFactory;
        this.jndiNameFactory = jndiNameFactory;
    }

    @Override
    public Iterable<ServiceInstaller> apply(CapabilityServiceSupport support, String value) {
        ServiceInstaller installer = this.installerFactory.apply(support, value);
        if (this.jndiNameFactory == null) {
            return List.of(installer);
        }
        ContextNames.BindInfo binding = ContextNames.bindInfoFor(this.jndiNameFactory.apply(value).getAbsoluteName());
        ServiceName name = support.getCapabilityServiceName(this.descriptor, value);
        return List.of(installer, new BinderServiceInstaller(binding, name));
    }
}
