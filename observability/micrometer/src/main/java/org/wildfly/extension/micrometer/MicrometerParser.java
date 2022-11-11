/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2022 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.extension.micrometer;

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;

import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentResourceXMLParser;
import org.wildfly.extension.micrometer.model.MicrometerSchema;

class MicrometerParser extends PersistentResourceXMLParser {
    private final MicrometerSchema schema;

    MicrometerParser(MicrometerSchema schema) {
        this.schema = schema;
    }

    @Override
    public PersistentResourceXMLDescription getParserDescription() {
        return  builder(org.wildfly.extension.micrometer.MicrometerSubsystemExtension.SUBSYSTEM_PATH, schema.getNamespaceUri())
                .addAttributes(MicrometerSubsystemDefinition.ATTRIBUTES)
                .build();
    }
}
