/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.server.service;

import java.util.Map;
import java.util.function.BiFunction;

import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.wildfly.subsystem.service.ServiceInstaller;

/**
 * SPI for providing services on behalf of a cache container.
 * @author Paul Ferraro
 */
public interface CacheContainerServiceInstallerProvider extends BiFunction<CapabilityServiceSupport, Map.Entry<String, String>, Iterable<ServiceInstaller>> {

}
