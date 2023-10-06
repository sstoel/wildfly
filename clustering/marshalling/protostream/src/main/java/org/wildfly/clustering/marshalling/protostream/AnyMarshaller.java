/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.marshalling.protostream;

import java.io.IOException;
import java.lang.reflect.Proxy;

import org.infinispan.protostream.BaseMarshaller;
import org.infinispan.protostream.ImmutableSerializationContext;
import org.infinispan.protostream.descriptors.WireType;

/**
 * Marshaller for an {@link Any} object.
 * @author Paul Ferraro
 */
public enum AnyMarshaller implements ProtoStreamMarshaller<Any> {
    INSTANCE;

    @Override
    public Class<? extends Any> getJavaClass() {
        return Any.class;
    }

    @Override
    public Any readFrom(ProtoStreamReader reader) throws IOException {
        Object value = null;
        while (!reader.isAtEnd()) {
            int tag = reader.readTag();
            AnyField field = AnyField.fromIndex(WireType.getTagFieldNumber(tag));
            if (field != null) {
                value = field.getMarshaller().readFrom(reader);
            } else {
                reader.skipField(tag);
            }
        }
        return new Any(value);
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, Any value) throws IOException {
        Object object = value.get();
        if (object != null) {
            AnyField field = getField(writer, object);
            writer.writeTag(field.getIndex(), field.getMarshaller().getWireType());
            field.getMarshaller().writeTo(writer, object);
        }
    }

    private static AnyField getField(ProtoStreamWriter writer, Object value) {
        if (value instanceof Reference) return AnyField.REFERENCE;

        ImmutableSerializationContext context = writer.getSerializationContext();
        Class<?> valueClass = value.getClass();
        AnyField field = AnyField.fromJavaType(valueClass);
        if (field != null) return field;

        if (value instanceof Enum) {
            Enum<?> enumValue = (Enum<?>) value;
            BaseMarshaller<?> marshaller = context.getMarshaller(enumValue.getDeclaringClass());
            return hasTypeId(context, marshaller) ? AnyField.IDENTIFIED_ENUM : AnyField.NAMED_ENUM;
        }

        if (valueClass.isArray()) {
            Class<?> componentType = valueClass.getComponentType();
            AnyField componentTypeField = AnyField.fromJavaType(componentType);
            if (componentTypeField != null) return AnyField.FIELD_ARRAY;
            try {
                BaseMarshaller<?> marshaller = context.getMarshaller(componentType);
                return hasTypeId(context, marshaller) ? AnyField.IDENTIFIED_ARRAY : AnyField.NAMED_ARRAY;
            } catch (IllegalArgumentException e) {
                return AnyField.ANY_ARRAY;
            }
        }

        if (Proxy.isProxyClass(valueClass)) {
            return AnyField.PROXY;
        }

        BaseMarshaller<?> marshaller = writer.findMarshaller(valueClass);
        return hasTypeId(context, marshaller) ? AnyField.IDENTIFIED_OBJECT : AnyField.NAMED_OBJECT;
    }

    private static boolean hasTypeId(ImmutableSerializationContext context, BaseMarshaller<?> marshaller) {
        return context.getDescriptorByName(marshaller.getTypeName()).getTypeId() != null;
    }
}
