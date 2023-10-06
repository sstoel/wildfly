/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.deployment.classloading.ear;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
public class EarJbossStructureCascadeExclusionsTestCase {

    @Deployment
    public static Archive<?> deploy() {
        WebArchive war = ShrinkWrap.create(WebArchive.class, "test.war");
        war.addClasses(TestAA.class, EarJbossStructureCascadeExclusionsTestCase.class);

        EnterpriseArchive ear = ShrinkWrap.create(EnterpriseArchive.class);
        ear.addAsModule(war);
        //test the 1.3 structure parser cascade exclusions
        ear.addAsManifestResource(new StringAsset(
                        "<jboss-deployment-structure xmlns=\"urn:jboss:deployment-structure:1.3\">" +
                        "<ear-exclusions-cascaded-to-subdeployments>true</ear-exclusions-cascaded-to-subdeployments>" +
                        "<deployment>" +
                        "   <exclusions>" +
                        "      <module name=\"org.jboss.logging\" />" +
                        "   </exclusions>" +
                        "</deployment>" +
                        "<sub-deployment name=\"test.war\">" +
                        "   <dependencies>" +
                        "       <module name=\"org.jboss.classfilewriter\" />" +
                        "   </dependencies>" +
                        "</sub-deployment>" +
                "</jboss-deployment-structure>"),
                "jboss-deployment-structure.xml");
        return ear;
    }

    @Test(expected = ClassNotFoundException.class)
    public void testWarDoesNotHaveAccessToClassJbossLoggingLogger() throws ClassNotFoundException {
        loadClass("org.jboss.logging.Logger", getClass().getClassLoader());
    }

    @Test
    public void testWarDoesHaveAccessToClassSlf4jLogger() throws ClassNotFoundException {
        loadClass("org.slf4j.Logger", getClass().getClassLoader());
    }

    private static Class<?> loadClass(String name, ClassLoader cl) throws ClassNotFoundException {
        return Class.forName(name, false, cl);
    }
}
