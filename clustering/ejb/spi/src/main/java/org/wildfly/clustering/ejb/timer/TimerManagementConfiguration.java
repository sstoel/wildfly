/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.ejb.timer;

import java.util.function.Function;

import org.jboss.modules.Module;
import org.wildfly.clustering.marshalling.spi.ByteBufferMarshaller;

/**
 * Encapsulates the configuration of a {@link TimerManagementProvider}.
 * @author Paul Ferraro
 */
public interface TimerManagementConfiguration {

    Function<Module, ByteBufferMarshaller> getMarshallerFactory();

    Integer getMaxActiveTimers();
}
