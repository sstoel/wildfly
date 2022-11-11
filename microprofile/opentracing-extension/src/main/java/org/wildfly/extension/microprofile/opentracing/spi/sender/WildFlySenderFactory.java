/*
 * Copyright 2021 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.extension.microprofile.opentracing.spi.sender;

import io.jaegertracing.Configuration;
import io.jaegertracing.internal.senders.NoopSender;
import io.jaegertracing.spi.Sender;
import io.jaegertracing.spi.SenderFactory;
import io.jaegertracing.thrift.internal.senders.ThriftSenderFactory;
import org.wildfly.extension.microprofile.opentracing.TracingExtensionLogger;

/**
 * Jaeger client SenderFactory implementation to be able to 'swallow' exceptions when sending the spans to a Jaeger server.
 * @author Emmanuel Hugonnet (c) 2020 Red Hat, Inc.
 */
public class WildFlySenderFactory implements SenderFactory {

    private final SenderFactory delegate = new ThriftSenderFactory();

    @Override
    public Sender getSender(Configuration.SenderConfiguration configuration) {
        if (!isSenderConfigured(configuration)) {
            TracingExtensionLogger.ROOT_LOGGER.senderNotConfigured();
            return new NoopSender();
        } else {
            return new WildFlySender(delegate.getSender(configuration));
        }
    }

    private boolean isSenderConfigured(Configuration.SenderConfiguration configuration) {
        return (configuration.getEndpoint() != null) ||
                (configuration.getAgentHost() != null) ||
                (configuration.getAgentPort() != null);
    }

    @Override
    public String getType() {
        return delegate.getType();
    }
}
