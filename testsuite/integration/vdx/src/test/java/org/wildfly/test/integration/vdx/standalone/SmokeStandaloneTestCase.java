/*
 * Copyright 2016 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  *
 * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.wildfly.test.integration.vdx.standalone;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.test.shared.util.AssumeTestGroupUtil;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.wildfly.test.integration.vdx.TestBase;
import org.wildfly.test.integration.vdx.category.StandaloneTests;
import org.wildfly.test.integration.vdx.utils.server.ServerConfig;

import java.nio.file.Files;

/**
 * Smoke test case - it tests whether WildFly/EAP test automation is working and basic VDX functionality.
 */
@RunAsClient
@RunWith(Arquillian.class)
@Category(StandaloneTests.class)
public class SmokeStandaloneTestCase extends TestBase {

    @Test
    @ServerConfig(configuration = "duplicate-attribute.xml")
    public void testWithExistingConfigInResources() throws Exception {
        container().tryStartAndWaitForFail();
        ensureDuplicateAttribute(container().getErrorMessageFromServerStart());
    }
    public static void ensureDuplicateAttribute(String errorMessages) {
        assertContains(errorMessages, "OPVDX001: Validation error in duplicate-attribute.xml");
        assertContains(errorMessages, "<jdbc data-source=\"foo\"");
        assertContains(errorMessages, "data-source=\"bar\"/>");
        if (errorMessages.contains("first appears")) {
            // Apache JAXP impl
            assertContains(errorMessages, "^^^^ 'data-source' can't appear more than once on this element");
            assertContains(errorMessages, "A 'data-source' attribute first appears here:");
        } else {
            // JDK JAXP impl
            assertContains(errorMessages, "^^^^ http://www.w3.org/TR/1999/REC-xml-names-19990114#AttributeNotUnique?jdbc&data-source");
        }
    }

    @Test
    @ServerConfig(configuration = "standalone-full-ha-to-damage.xml", xmlTransformationGroovy = "TypoInExtensions.groovy")
    public void typoInExtensionsWithConfigInResources() throws Exception {
        container().tryStartAndWaitForFail();
        ensureTypoInExtensions(container().getErrorMessageFromServerStart());
    }
    public static void ensureTypoInExtensions(String errorMessages) {
        assertContains(errorMessages, "WFLYCTL0197: Unexpected attribute 'modules' encountered");
    }

    @Test
    @ServerConfig(configuration = "standalone-full-ha.xml", xmlTransformationGroovy = "AddNonExistentElementToMessagingSubsystem.groovy",
        subtreeName = "messaging", subsystemName = "messaging-activemq")
    public void addNonExistingElementToMessagingSubsystem() throws Exception {
        // WildFly Preview doesn't configure a messaging broker
        AssumeTestGroupUtil.assumeNotWildFlyPreview();
        container().tryStartAndWaitForFail();
        ensureNonExistingElementToMessagingSubsystem(container().getErrorMessageFromServerStart());
    }
    public static void ensureNonExistingElementToMessagingSubsystem(String errorMessages) {
        assertContains(errorMessages, "^^^^ 'id' isn't an allowed attribute for the 'cluster' element");
        assertContains(errorMessages, "| 'id' is allowed on elements:");
        assertContains(errorMessages, "resource-adapters > resource-adapter");
        assertContains(errorMessages, "resource-adapters > resource-adapter > module");
    }

    @Test
    @ServerConfig(configuration = "empty.xml")
    public void emptyConfigFile() throws Exception {
        container().tryStartAndWaitForFail();
        assertContains( String.join("\n", Files.readAllLines(container().getServerLogPath())),
                "OPVDX004: Failed to pretty print validation error: empty.xml has no content");
    }
}
