/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.marshalling.protostream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.infinispan.protostream.ProtobufTagMarshaller.ReadContext;
import org.infinispan.protostream.TagReader;

/**
 * {@link ProtoStreamWriter} implementation that reads from a {@link TagReader}.
 * @author Paul Ferraro
 */
public class DefaultProtoStreamReader extends AbstractProtoStreamOperation implements ProtoStreamReader, ReadContext {

    interface ProtoStreamReaderContext extends ProtoStreamOperation.Context {
        /**
         * Resolves an object from the specified reference.
         * @param reference an object reference
         * @return the resolved object
         */
        Object resolve(Reference reference);
    }

    private final TagReader reader;
    private final ProtoStreamReaderContext context;

    public DefaultProtoStreamReader(ReadContext context) {
        this(context, new DefaultProtoStreamReaderContext());
    }

    private DefaultProtoStreamReader(ReadContext context, ProtoStreamReaderContext readerContext) {
        super(context);
        this.reader = context.getReader();
        this.context = readerContext;
    }

    @Override
    public Object readAny() throws IOException {
        Object result = this.readObject(Any.class).get();
        if (result instanceof Reference) {
            Reference reference = (Reference) result;
            result = this.context.resolve(reference);
        } else {
            this.context.record(result);
        }
        return result;
    }

    @Override
    public Context getContext() {
        return this.context;
    }

    @Override
    public TagReader getReader() {
        return this.reader;
    }

    @Override
    public <T> T readObject(Class<T> targetClass) throws IOException {
        int limit = this.reader.readUInt32();
        int oldLimit = this.reader.pushLimit(limit);
        try {
            ProtoStreamMarshaller<T> marshaller = this.findMarshaller(targetClass);
            T result = marshaller.readFrom(this);
            // Ensure marshaller reached limit
            this.reader.checkLastTagWas(0);
            return result;
        } finally {
            this.reader.popLimit(oldLimit);
        }
    }

    @Override
    public int pushLimit(int limit) throws IOException {
        return this.reader.pushLimit(limit);
    }

    @Override
    public void popLimit(int oldLimit) {
        this.reader.popLimit(oldLimit);
    }

    @Override
    public boolean isAtEnd() throws IOException {
        return this.reader.isAtEnd();
    }

    @Override
    public int readTag() throws IOException {
        return this.reader.readTag();
    }

    @Override
    public void checkLastTagWas(int tag) throws IOException {
        this.reader.checkLastTagWas(tag);
    }

    @Override
    public boolean skipField(int tag) throws IOException {
        return this.reader.skipField(tag);
    }

    @Override
    public boolean readBool() throws IOException {
        return this.reader.readBool();
    }

    @Override
    public int readEnum() throws IOException {
        return this.reader.readEnum();
    }

    @Deprecated
    @Override
    public int readInt32() throws IOException {
        return this.reader.readInt32();
    }

    @Deprecated
    @Override
    public int readFixed32() throws IOException {
        return this.reader.readFixed32();
    }

    @Override
    public int readUInt32() throws IOException {
        return this.reader.readUInt32();
    }

    @Override
    public int readSInt32() throws IOException {
        return this.reader.readSInt32();
    }

    @Override
    public int readSFixed32() throws IOException {
        return this.reader.readSFixed32();
    }

    @Deprecated
    @Override
    public long readInt64() throws IOException {
        return this.reader.readInt64();
    }

    @Deprecated
    @Override
    public long readFixed64() throws IOException {
        return this.reader.readFixed64();
    }

    @Override
    public long readUInt64() throws IOException {
        return this.reader.readUInt64();
    }

    @Override
    public long readSInt64() throws IOException {
        return this.reader.readSInt64();
    }

    @Override
    public long readSFixed64() throws IOException {
        return this.reader.readSFixed64();
    }

    @Override
    public float readFloat() throws IOException {
        return this.reader.readFloat();
    }

    @Override
    public double readDouble() throws IOException {
        return this.reader.readDouble();
    }

    @Override
    public byte[] readByteArray() throws IOException {
        return this.reader.readByteArray();
    }

    @Override
    public ByteBuffer readByteBuffer() throws IOException {
        return this.reader.readByteBuffer();
    }

    @Override
    public String readString() throws IOException {
        return this.reader.readString();
    }

    @Override
    public byte[] fullBufferArray() throws IOException {
        return this.reader.fullBufferArray();
    }

    @Override
    public InputStream fullBufferInputStream() throws IOException {
        return this.reader.fullBufferInputStream();
    }

    @Override
    public boolean isInputStream() {
        return this.reader.isInputStream();
    }

    private static class DefaultProtoStreamReaderContext implements ProtoStreamReaderContext {
        private final Map<Object, Boolean> objects = new IdentityHashMap<>(128);
        private final List<Object> references = new ArrayList<>(128);

        @Override
        public void record(Object object) {
            if (object != null) {
                if (this.objects.putIfAbsent(object, Boolean.TRUE) == null) {
                    this.references.add(object);
                }
            }
        }

        @Override
        public Object resolve(Reference reference) {
            return this.references.get(reference.getAsInt());
        }
    }
}
