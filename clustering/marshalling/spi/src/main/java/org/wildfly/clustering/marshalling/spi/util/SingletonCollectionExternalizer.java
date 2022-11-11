/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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

package org.wildfly.clustering.marshalling.spi.util;

import java.util.Collection;
import java.util.function.Function;

import org.wildfly.clustering.marshalling.spi.ObjectExternalizer;

/**
 * Externalizer for singleton collections.
 * @author Paul Ferraro
 */
public class SingletonCollectionExternalizer<T extends Collection<Object>> extends ObjectExternalizer<T> {
    private static final Function<Collection<Object>, Object> ACCESSOR = new Function<>() {
        @Override
        public Object apply(Collection<Object> collection) {
            return collection.iterator().next();
        }
    };

    @SuppressWarnings("unchecked")
    public SingletonCollectionExternalizer(Function<Object, T> factory) {
        super((Class<T>) factory.apply(null).getClass(), factory, accessor());
    }

    @SuppressWarnings("unchecked")
    public static <T extends Collection<Object>> Function<T, Object> accessor() {
        return (Function<T, Object>) ACCESSOR;
    }
}
