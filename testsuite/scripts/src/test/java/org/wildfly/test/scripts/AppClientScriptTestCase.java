/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.scripts;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.junit.Assert;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class AppClientScriptTestCase extends ScriptTestCase {

    public AppClientScriptTestCase() {
        super("appclient");
    }

    @Override
    void testScript(final ScriptProcess script) throws InterruptedException, TimeoutException, IOException {
        // First check the standard script
        script.start(MAVEN_JAVA_OPTS, "-v");
        testScript(script, 0);

        // Test with the security manager enabled
        script.start(MAVEN_JAVA_OPTS, "-v", "-secmgr");
        testScript(script, jvmVersion() >= 17 ? 4 : 0);
    }

    private void testScript(final ScriptProcess script, final int additionalLines) throws InterruptedException, IOException {
        try (script) {
            validateProcess(script);

            final List<String> lines = script.getStdout();
            int count = 2 + additionalLines;
            for (String stdout : lines) {
                if (stdout.startsWith("Picked up")) {
                    count += 1;
                }
            }
            final int expectedLines = (script.getShell() == Shell.BATCH ? 3 + additionalLines : count);
            Assert.assertEquals(script.getErrorMessage(String.format("Expected %d lines.", expectedLines)), expectedLines,
                    lines.size());
        }
    }
}
