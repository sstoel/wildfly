/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.clustering.jgroups.subsystem;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.jboss.as.clustering.controller.Attribute;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public enum XMLElement {
    // must be first
    UNKNOWN(""),

    AUTH_PROTOCOL("auth-protocol"),
    CIPHER_TOKEN("cipher-token"),
    CHANNEL(ChannelResourceDefinition.WILDCARD_PATH),
    CHANNELS("channels"),
    DEFAULT_THREAD_POOL("default-thread-pool"),
    DIGEST_TOKEN("digest-token"),
    ENCRYPT_PROTOCOL("encrypt-protocol"),
    FORK(ForkResourceDefinition.WILDCARD_PATH),
    INTERNAL_THREAD_POOL("internal-thread-pool"),
    JDBC_PROTOCOL("jdbc-protocol"),
    KEY_CREDENTIAL_REFERENCE(EncryptProtocolResourceDefinition.Attribute.KEY_CREDENTIAL),
    OOB_THREAD_POOL("oob-thread-pool"),
    PLAIN_TOKEN("plain-token"),
    PROPERTY(ModelDescriptionConstants.PROPERTY),
    PROTOCOL(ProtocolResourceDefinition.WILDCARD_PATH),
    RELAY(RelayResourceDefinition.WILDCARD_PATH),
    REMOTE_SITE(RemoteSiteResourceDefinition.WILDCARD_PATH),
    SHARED_SECRET_CREDENTIAL_REFERENCE(AuthTokenResourceDefinition.Attribute.SHARED_SECRET),
    SOCKET_PROTOCOL("socket-protocol"),
    SOCKET_DISCOVERY_PROTOCOL("socket-discovery-protocol"),
    STACK(StackResourceDefinition.WILDCARD_PATH),
    STACKS("stacks"),
    TIMER_THREAD_POOL("timer-thread-pool"),
    TRANSPORT(TransportResourceDefinition.WILDCARD_PATH),
    ;

    private final String name;

    XMLElement(PathElement path) {
        this.name = path.isWildcard() ? path.getKey() : path.getValue();
    }

    XMLElement(Attribute attribute) {
        this.name = attribute.getName();
    }

    XMLElement(String name) {
        this.name = name;
    }

    /**
     * Get the local name of this element.
     *
     * @return the local name
     */
    public String getLocalName() {
        return this.name;
    }

    @Override
    public String toString() {
        return this.name;
    }

    private enum XMLElementFunction implements Function<ModelNode, XMLElement> {
        PROTOCOL(XMLElement.PROTOCOL),
        SOCKET_PROTOCOL(XMLElement.SOCKET_PROTOCOL),
        JDBC_PROTOCOL(XMLElement.JDBC_PROTOCOL),
        ENCRYPT_PROTOCOL(XMLElement.ENCRYPT_PROTOCOL),
        SOCKET_DISCOVERY_PROTOCOL(XMLElement.SOCKET_DISCOVERY_PROTOCOL),
        AUTH_PROTOCOL(XMLElement.AUTH_PROTOCOL),
        ;
        private final XMLElement element;

        XMLElementFunction(XMLElement element) {
            this.element = element;
        }

        @Override
        public XMLElement apply(ModelNode ignored) {
            return this.element;
        }
    }

    private static final Map<String, XMLElement> elements = new HashMap<>();
    private static final Map<String, Function<ModelNode, XMLElement>> protocols = new HashMap<>();
    private static final Map<String, XMLElement> tokens = new HashMap<>();

    static {
        for (XMLElement element : values()) {
            String name = element.getLocalName();
            if (name != null) {
                elements.put(name, element);
            }
        }

        Function<ModelNode, XMLElement> function = new Function<>() {
            @Override
            public XMLElement apply(ModelNode model) {
                // Use socket-protocol element only if optional socket-binding was defined
                return model.hasDefined(SocketProtocolResourceDefinition.Attribute.SOCKET_BINDING.getName()) ? XMLElement.SOCKET_PROTOCOL : XMLElement.PROTOCOL;
            }
        };
        for (ProtocolResourceRegistrar.SocketProtocol protocol : EnumSet.allOf(ProtocolResourceRegistrar.SocketProtocol.class)) {
            protocols.put(protocol.name(), function);
        }
        for (ProtocolResourceRegistrar.MulticastProtocol protocol : EnumSet.allOf(ProtocolResourceRegistrar.MulticastProtocol.class)) {
            protocols.put(protocol.name(), XMLElementFunction.SOCKET_PROTOCOL);
        }
        for (ProtocolResourceRegistrar.JdbcProtocol protocol : EnumSet.allOf(ProtocolResourceRegistrar.JdbcProtocol.class)) {
            protocols.put(protocol.name(), XMLElementFunction.JDBC_PROTOCOL);
        }
        for (ProtocolResourceRegistrar.EncryptProtocol protocol : EnumSet.allOf(ProtocolResourceRegistrar.EncryptProtocol.class)) {
            protocols.put(protocol.name(), XMLElementFunction.ENCRYPT_PROTOCOL);
        }
        for (ProtocolResourceRegistrar.InitialHostsProtocol protocol : EnumSet.allOf(ProtocolResourceRegistrar.InitialHostsProtocol.class)) {
            protocols.put(protocol.name(), XMLElementFunction.SOCKET_DISCOVERY_PROTOCOL);
        }
        for (ProtocolResourceRegistrar.AuthProtocol protocol : EnumSet.allOf(ProtocolResourceRegistrar.AuthProtocol.class)) {
            protocols.put(protocol.name(), XMLElementFunction.AUTH_PROTOCOL);
        }

        tokens.put(PlainAuthTokenResourceDefinition.PATH.getValue(), XMLElement.PLAIN_TOKEN);
        tokens.put(DigestAuthTokenResourceDefinition.PATH.getValue(), XMLElement.DIGEST_TOKEN);
        tokens.put(CipherAuthTokenResourceDefinition.PATH.getValue(), XMLElement.CIPHER_TOKEN);
    }

    public static XMLElement forName(String localName) {
        return elements.getOrDefault(localName, UNKNOWN);
    }

    public static XMLElement forProtocolName(Property protocol) {
        return protocols.getOrDefault(protocol.getName(), XMLElementFunction.PROTOCOL).apply(protocol.getValue());
    }

    public static XMLElement forAuthTokenName(String token) {
        XMLElement element = tokens.get(token);
        if (element == null) throw new IllegalArgumentException(token);
        return element;
    }
}
