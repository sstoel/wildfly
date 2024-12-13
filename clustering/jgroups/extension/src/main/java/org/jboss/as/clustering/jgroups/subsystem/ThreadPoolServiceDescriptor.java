/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.clustering.jgroups.subsystem;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceRegistration;
import org.wildfly.clustering.jgroups.spi.ChannelFactory;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;

/**
 * @author Paul Ferraro
 */
public interface ThreadPoolServiceDescriptor extends UnaryServiceDescriptor<ThreadPoolConfiguration>, ResourceRegistration {

    @Override
    default String getName() {
        PathElement path = this.getPathElement();
        return String.join(".", ChannelFactory.SERVICE_DESCRIPTOR.getName(), path.getKey(), path.getValue());
    }

    @Override
    default Class<ThreadPoolConfiguration> getType() {
        return ThreadPoolConfiguration.class;
    }
}
