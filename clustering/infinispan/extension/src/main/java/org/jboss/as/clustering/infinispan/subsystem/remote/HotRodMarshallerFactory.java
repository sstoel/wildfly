/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.clustering.infinispan.subsystem.remote;

import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.infinispan.commons.marshall.Marshaller;
import org.infinispan.query.remote.client.impl.MarshallerRegistration;
import org.jboss.as.clustering.infinispan.marshalling.UserMarshallerFactory;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoader;

/**
 * @author Paul Ferraro
 */
public enum HotRodMarshallerFactory implements BiFunction<ModuleLoader, List<Module>, Marshaller> {

    LEGACY() {
        private final Set<String> protoStreamModules = Set.of("org.wildfly.clustering.web.hotrod");
        private final Predicate<String> protoStreamPredicate = this.protoStreamModules::contains;

        @Override
        public Marshaller apply(ModuleLoader moduleLoader, List<Module> modules) {
            // Choose marshaller based on the associated modules
            return (modules.stream().map(Module::getName).anyMatch(this.protoStreamPredicate) ? PROTOSTREAM : JBOSS).apply(moduleLoader, modules);
        }
    },
    JBOSS() {
        @Override
        public Marshaller apply(ModuleLoader moduleLoader, List<Module> modules) {
            return UserMarshallerFactory.JBOSS.createUserMarshaller(moduleLoader, modules.stream().map(Module::getClassLoader).collect(Collectors.toList()));
        }
    },
    PROTOSTREAM() {
        @Override
        public Marshaller apply(ModuleLoader moduleLoader, List<Module> modules) {
            return (modules.contains(Module.forClass(MarshallerRegistration.class)) ? UserMarshallerFactory.DEFAULT : UserMarshallerFactory.PROTOSTREAM).createUserMarshaller(moduleLoader, modules.stream().map(Module::getClassLoader).collect(Collectors.toList()));
        }
    },
    ;
}
