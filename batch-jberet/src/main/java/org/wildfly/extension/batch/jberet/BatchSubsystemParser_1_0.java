/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.batch.jberet;

import static org.jboss.as.threads.Namespace.THREADS_1_1;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.threads.ThreadsParser;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.wildfly.extension.batch.jberet.job.repository.InMemoryJobRepositoryDefinition;
import org.wildfly.extension.batch.jberet.job.repository.JdbcJobRepositoryDefinition;
import org.wildfly.extension.batch.jberet.thread.pool.BatchThreadPoolResourceDefinition;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class BatchSubsystemParser_1_0 implements XMLStreamConstants, XMLElementReader<List<ModelNode>> {

    private final Map<Element, SimpleAttributeDefinition> attributeElements;

    public BatchSubsystemParser_1_0() {
        this(Collections.emptyMap());
    }

    BatchSubsystemParser_1_0(final Map<Element, SimpleAttributeDefinition> additionalElements) {
        attributeElements = new HashMap<>(additionalElements);
        attributeElements.put(Element.DEFAULT_JOB_REPOSITORY, BatchSubsystemDefinition.DEFAULT_JOB_REPOSITORY);
        attributeElements.put(Element.DEFAULT_THREAD_POOL, BatchSubsystemDefinition.DEFAULT_THREAD_POOL);
        attributeElements.put(Element.RESTART_JOBS_ON_RESUME, BatchSubsystemDefinition.RESTART_JOBS_ON_RESUME);
    }

    @Override
    public void readElement(final XMLExtendedStreamReader reader, final List<ModelNode> ops) throws XMLStreamException {
        final ThreadsParser threadsParser = ThreadsParser.getInstance();
        final PathAddress subsystemAddress = PathAddress.pathAddress(BatchSubsystemDefinition.SUBSYSTEM_PATH);
        // Add the subsystem
        final ModelNode subsystemAddOp = Util.createAddOperation(subsystemAddress);
        ops.add(subsystemAddOp);

        // Find the required elements
        final Set<Element> requiredElements = EnumSet.of(Element.JOB_REPOSITORY, Element.THREAD_POOL);
        attributeElements.forEach((element, attribute) -> {
            if (!attribute.isNillable() && attribute.getDefaultValue() == null) {
                requiredElements.add(element);
            }
        });

        final Namespace namespace = Namespace.forUri(reader.getNamespaceURI());

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final String localName = reader.getLocalName();
            final Element element = Element.forName(localName);
            final SimpleAttributeDefinition attribute = attributeElements.get(element);
            if (attribute != null) {
                final AttributeParser parser = attribute.getParser();
                if (parser.isParseAsElement()) {
                    parser.parseElement(attribute, reader, subsystemAddOp);
                } else {
                    // Assume this is an element with a single name attribute
                    parser.parseAndSetParameter(attribute, AttributeParsers.readNameAttribute(reader), subsystemAddOp, reader);
                    ParseUtils.requireNoContent(reader);
                }
                requiredElements.remove(element);
            } else if (element == Element.JOB_REPOSITORY) {
                parseJobRepository(reader, subsystemAddress, ops);
                requiredElements.remove(Element.JOB_REPOSITORY);
            } else if (element == Element.THREAD_POOL) {
                threadsParser.parseUnboundedQueueThreadPool(reader, namespace.getUriString(),
                        THREADS_1_1, subsystemAddress.toModelNode(), ops,
                        BatchThreadPoolResourceDefinition.NAME, null);
                requiredElements.remove(Element.THREAD_POOL);
            } else if (element == Element.THREAD_FACTORY) {
                threadsParser.parseThreadFactory(reader, namespace.getUriString(),
                        THREADS_1_1, subsystemAddress.toModelNode(), ops,
                        BatchSubsystemDefinition.THREAD_FACTORY, null);
            } else {
                throw ParseUtils.unexpectedElement(reader);
            }
        }
        if (!requiredElements.isEmpty()) {
            throw ParseUtils.missingRequired(reader, requiredElements);
        }
        ParseUtils.requireNoContent(reader);
    }

    protected void parseJobRepository(final XMLExtendedStreamReader reader, final PathAddress subsystemAddress, final List<ModelNode> ops) throws XMLStreamException {
        final String name = AttributeParsers.readRequiredAttributes(reader, EnumSet.of(Attribute.NAME)).get(Attribute.NAME);

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final String localName = reader.getLocalName();
            final Element element = Element.forName(localName);
            if (element == Element.IN_MEMORY) {
                ops.add(Util.createAddOperation(subsystemAddress.append(InMemoryJobRepositoryDefinition.NAME, name)));
                ParseUtils.requireNoContent(reader);
            } else if (element == Element.JDBC) {
                final Map<Attribute, String> attributes = AttributeParsers.readRequiredAttributes(reader, EnumSet.of(Attribute.DATA_SOURCE));
                final ModelNode op = Util.createAddOperation(subsystemAddress.append(JdbcJobRepositoryDefinition.NAME, name));
                JdbcJobRepositoryDefinition.DATA_SOURCE.parseAndSetParameter(attributes.get(Attribute.DATA_SOURCE), op, reader);
                ops.add(op);
                ParseUtils.requireNoContent(reader);
            } else {
                throw ParseUtils.unexpectedElement(reader);
            }
        }
    }
}