/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019, Red Hat, Inc., and individual contributors
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

package org.wildfly.clustering.infinispan.marshalling.jboss;

import java.util.AbstractMap;

import org.infinispan.commons.dataconversion.MediaType;
import org.jboss.marshalling.ClassResolver;
import org.wildfly.clustering.infinispan.marshalling.UserMarshaller;
import org.wildfly.clustering.marshalling.jboss.JBossByteBufferMarshaller;
import org.wildfly.clustering.marshalling.jboss.MarshallingConfigurationRepository;
import org.wildfly.clustering.marshalling.jboss.SimpleMarshallingConfigurationRepository;

/**
 * @author Paul Ferraro
 */
public class JBossMarshaller extends UserMarshaller {

    public JBossMarshaller(ClassResolver resolver, ClassLoader loader) {
        this(new SimpleMarshallingConfigurationRepository(JBossMarshallingVersion.class, JBossMarshallingVersion.CURRENT, new AbstractMap.SimpleImmutableEntry<>(resolver, loader)), loader);
    }

    public JBossMarshaller(MarshallingConfigurationRepository repository, ClassLoader loader) {
        super(MediaType.APPLICATION_JBOSS_MARSHALLING, new JBossByteBufferMarshaller(repository, loader));
    }
}
