/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.ejb.infinispan.bean;

import java.util.function.Function;

import org.kohsuke.MetaInfServices;
import org.wildfly.clustering.ejb.bean.BeanManagementProvider;
import org.wildfly.clustering.ejb.bean.BeanDeploymentMarshallingContext;
import org.wildfly.clustering.ejb.bean.LegacyBeanManagementConfiguration;
import org.wildfly.clustering.ejb.bean.LegacyBeanManagementProviderFactory;
import org.wildfly.clustering.ejb.cache.bean.BeanMarshallerFactory;
import org.wildfly.clustering.marshalling.spi.ByteBufferMarshaller;

/**
 * A {@link LegacyBeanManagementProviderFactory} implementation that creates a suitable {@link BeanManagementProvider} when no distributable-ejb subsystem is present.
 * @author Paul Ferraro
 * @deprecated This exists to support legacy configuration that does not define a distributable-ejb subsystem.
 */
@Deprecated
@MetaInfServices(LegacyBeanManagementProviderFactory.class)
public class InfinispanLegacyBeanManagementProviderFactory implements LegacyBeanManagementProviderFactory {

    @Override
    public BeanManagementProvider createBeanManagementProvider(String name, LegacyBeanManagementConfiguration config) {
        return new InfinispanBeanManagementProvider(name, new InfinispanBeanManagementConfiguration() {
            @Override
            public String getContainerName() {
                return config.getContainerName();
            }

            @Override
            public String getCacheName() {
                return config.getCacheName();
            }

            @Override
            public Integer getMaxActiveBeans() {
                return config.getMaxActiveBeans();
            }

            @Override
            public Function<BeanDeploymentMarshallingContext, ByteBufferMarshaller> getMarshallerFactory() {
                return BeanMarshallerFactory.JBOSS;
            }
        });
    }
}
