/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.marshalling.protostream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.infinispan.protostream.descriptors.WireType;

/**
 * Enumeration of common scalar marshaller implementations.
 * @author Paul Ferraro
 */
public enum Scalar implements ScalarMarshallerProvider {

    ANY(new ScalarMarshaller<Object>() {
        @Override
        public Object readFrom(ProtoStreamReader reader) throws IOException {
            return reader.readAny();
        }

        @Override
        public void writeTo(ProtoStreamWriter writer, Object value) throws IOException {
            writer.writeAnyNoTag(value);
        }

        @Override
        public Class<? extends Object> getJavaClass() {
            return Object.class;
        }

        @Override
        public WireType getWireType() {
            return WireType.LENGTH_DELIMITED;
        }
    }),
    BOOLEAN(new ScalarMarshaller<Boolean>() {
        @Override
        public Boolean readFrom(ProtoStreamReader reader) throws IOException {
            return reader.readUInt32() != 0;
        }

        @Override
        public void writeTo(ProtoStreamWriter writer, Boolean value) throws IOException {
            writer.writeVarint32(value ? 1 : 0);
        }

        @Override
        public Class<? extends Boolean> getJavaClass() {
            return Boolean.class;
        }

        @Override
        public WireType getWireType() {
            return WireType.VARINT;
        }
    }),
    BYTE(new ScalarMarshaller<Byte>() {
        @Override
        public Byte readFrom(ProtoStreamReader reader) throws IOException {
            return (byte) reader.readSInt32();
        }

        @Override
        public void writeTo(ProtoStreamWriter writer, Byte value) throws IOException {
            // Roll the bits in order to move the sign bit from position 31 to position 0, to reduce the wire length of negative numbers.
            // See https://developers.google.com/protocol-buffers/docs/encoding
            writer.writeVarint32((value << 1) ^ (value >> (Integer.SIZE - 1)));
        }

        @Override
        public Class<? extends Byte> getJavaClass() {
            return Byte.class;
        }

        @Override
        public WireType getWireType() {
            return WireType.VARINT;
        }
    }),
    SHORT(new ScalarMarshaller<Short>() {
        @Override
        public Short readFrom(ProtoStreamReader reader) throws IOException {
            return (short) reader.readSInt32();
        }

        @Override
        public void writeTo(ProtoStreamWriter writer, Short value) throws IOException {
            // Roll the bits in order to move the sign bit from position 31 to position 0, to reduce the wire length of negative numbers.
            // See https://developers.google.com/protocol-buffers/docs/encoding
            writer.writeVarint32((value << 1) ^ (value >> (Integer.SIZE - 1)));
        }

        @Override
        public Class<? extends Short> getJavaClass() {
            return Short.class;
        }

        @Override
        public WireType getWireType() {
            return WireType.VARINT;
        }
    }),
    INTEGER(new ScalarMarshaller<Integer>() {
        @Override
        public Integer readFrom(ProtoStreamReader reader) throws IOException {
            return reader.readSInt32();
        }

        @Override
        public void writeTo(ProtoStreamWriter writer, Integer value) throws IOException {
            // Roll the bits in order to move the sign bit from position 31 to position 0, to reduce the wire length of negative numbers.
            // See https://developers.google.com/protocol-buffers/docs/encoding
            writer.writeVarint32((value << 1) ^ (value >> (Integer.SIZE - 1)));
        }

        @Override
        public Class<? extends Integer> getJavaClass() {
            return Integer.class;
        }

        @Override
        public WireType getWireType() {
            return WireType.VARINT;
        }
    }),
    LONG(new ScalarMarshaller<Long>() {
        @Override
        public Long readFrom(ProtoStreamReader reader) throws IOException {
            return reader.readSInt64();
        }

        @Override
        public void writeTo(ProtoStreamWriter writer, Long value) throws IOException {
            // Roll the bits in order to move the sign bit from position 63 to position 0, to reduce the wire length of negative numbers.
            // See https://developers.google.com/protocol-buffers/docs/encoding
            writer.writeVarint64((value << 1) ^ (value >> (Long.SIZE - 1)));
        }

        @Override
        public Class<? extends Long> getJavaClass() {
            return Long.class;
        }

        @Override
        public WireType getWireType() {
            return WireType.VARINT;
        }
    }),
    FLOAT(new ScalarMarshaller<Float>() {
        @Override
        public Float readFrom(ProtoStreamReader reader) throws IOException {
            return reader.readFloat();
        }

        @Override
        public void writeTo(ProtoStreamWriter writer, Float value) throws IOException {
            int bits = Float.floatToRawIntBits(value);
            byte[] bytes = new byte[Float.BYTES];
            for (int i = 0; i < bytes.length; ++i) {
                int index = i * Byte.SIZE;
                bytes[i] = (byte) ((bits >> index) & 0xFF);
            }
            writer.writeRawBytes(bytes, 0, bytes.length);
        }

        @Override
        public Class<? extends Float> getJavaClass() {
            return Float.class;
        }

        @Override
        public WireType getWireType() {
            return WireType.FIXED32;
        }
    }),
    DOUBLE(new ScalarMarshaller<Double>() {
        @Override
        public Double readFrom(ProtoStreamReader reader) throws IOException {
            return reader.readDouble();
        }

        @Override
        public void writeTo(ProtoStreamWriter writer, Double value) throws IOException {
            long bits = Double.doubleToRawLongBits(value);
            byte[] bytes = new byte[Double.BYTES];
            for (int i = 0; i < bytes.length; ++i) {
                int index = i * Byte.SIZE;
                if (index < Integer.SIZE) {
                    bytes[i] = (byte) ((bits >> index) & 0xFF);
                } else {
                    bytes[i] = (byte) ((int) (bits >> index) & 0xFF);
                }
            }
            writer.writeRawBytes(bytes, 0, bytes.length);
        }

        @Override
        public Class<? extends Double> getJavaClass() {
            return Double.class;
        }

        @Override
        public WireType getWireType() {
            return WireType.FIXED64;
        }
    }),
    CHARACTER(new ScalarMarshaller<Character>() {
        @Override
        public Character readFrom(ProtoStreamReader reader) throws IOException {
            return (char) reader.readUInt32();
        }

        @Override
        public void writeTo(ProtoStreamWriter writer, Character value) throws IOException {
            writer.writeVarint32(value);
        }

        @Override
        public Class<? extends Character> getJavaClass() {
            return Character.class;
        }

        @Override
        public WireType getWireType() {
            return WireType.VARINT;
        }
    }),
    BYTE_BUFFER(new ScalarMarshaller<ByteBuffer>() {
        @Override
        public ByteBuffer readFrom(ProtoStreamReader reader) throws IOException {
            return reader.readByteBuffer();
        }

        @Override
        public void writeTo(ProtoStreamWriter writer, ByteBuffer buffer) throws IOException {
            if (buffer.hasArray()) {
                int length = buffer.remaining();
                writer.writeVarint32(length);
                writer.writeRawBytes(buffer.array(), buffer.arrayOffset(), length);
            } else {
                // Don't mess with existing position
                ByteBuffer copy = buffer.asReadOnlyBuffer();
                int length = copy.remaining();
                writer.writeVarint32(length);
                byte[] bytes = new byte[length];
                copy.get(bytes, copy.position(), length);
                writer.writeRawBytes(bytes, 0, length);
            }
        }

        @Override
        public Class<? extends ByteBuffer> getJavaClass() {
            return ByteBuffer.class;
        }

        @Override
        public WireType getWireType() {
            return WireType.LENGTH_DELIMITED;
        }
    }),
    BYTE_ARRAY(new ScalarMarshaller<byte[]>() {
        @Override
        public byte[] readFrom(ProtoStreamReader reader) throws IOException {
            return reader.readByteArray();
        }

        @Override
        public void writeTo(ProtoStreamWriter writer, byte[] value) throws IOException {
            writer.writeVarint32(value.length);
            writer.writeRawBytes(value, 0, value.length);
        }

        @Override
        public Class<? extends byte[]> getJavaClass() {
            return byte[].class;
        }

        @Override
        public WireType getWireType() {
            return WireType.LENGTH_DELIMITED;
        }
    }),
    STRING(new ScalarMarshaller<String>() {
        @Override
        public String readFrom(ProtoStreamReader reader) throws IOException {
            return StandardCharsets.UTF_8.decode(reader.readByteBuffer()).toString();
        }

        @Override
        public void writeTo(ProtoStreamWriter writer, String value) throws IOException {
            BYTE_BUFFER.writeTo(writer, StandardCharsets.UTF_8.encode(value));
        }

        @Override
        public Class<? extends String> getJavaClass() {
            return String.class;
        }

        @Override
        public WireType getWireType() {
            return WireType.LENGTH_DELIMITED;
        }
    }),
    REFERENCE(new ScalarMarshaller<Reference>() {
        @Override
        public Reference readFrom(ProtoStreamReader reader) throws IOException {
            return new Reference(reader.readUInt32());
        }

        @Override
        public void writeTo(ProtoStreamWriter writer, Reference value) throws IOException {
            writer.writeVarint32(value.getAsInt());
        }

        @Override
        public Class<? extends Reference> getJavaClass() {
            return Reference.class;
        }

        @Override
        public WireType getWireType() {
            return WireType.VARINT;
        }
    }),
    ;
    private final ScalarMarshaller<?> marshaller;

    Scalar(ScalarMarshaller<?> marshaller) {
        this.marshaller = marshaller;
    }

    @Override
    public ScalarMarshaller<?> getMarshaller() {
        return this.marshaller;
    }
}
