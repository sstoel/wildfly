/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.server.service;

import java.util.Collections;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.function.BiFunction;

import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.wildfly.subsystem.service.ServiceInstaller;

/**
 * Provides service installers of a given type.
 * @author Paul Ferraro
 */
public class ProvidedUnaryServiceInstallerProvider<P extends BiFunction<CapabilityServiceSupport, String, Iterable<ServiceInstaller>>> implements BiFunction<CapabilityServiceSupport, String, Iterable<ServiceInstaller>> {

    private final Class<P> providerType;
    private final ClassLoader loader;

    public ProvidedUnaryServiceInstallerProvider(Class<P> providerType, ClassLoader loader) {
        this.providerType = providerType;
        this.loader = loader;
    }

    @Override
    public Iterable<ServiceInstaller> apply(CapabilityServiceSupport support, String value) {
        Class<P> providerType = this.providerType;
        ClassLoader loader = this.loader;
        return new Iterable<> () {
            @Override
            public Iterator<ServiceInstaller> iterator() {
                return new Iterator<>() {
                    private final Iterator<P> providers = ServiceLoader.load(providerType, loader).iterator();
                    private Iterator<ServiceInstaller> installers = Collections.emptyIterator();

                    @Override
                    public boolean hasNext() {
                        return this.providers.hasNext() || this.installers.hasNext();
                    }

                    @Override
                    public ServiceInstaller next() {
                        while (!this.installers.hasNext()) {
                            this.installers = this.providers.next().apply(support, value).iterator();
                        }
                        return this.installers.next();
                    }
                };
            }
        };
    }
}
