/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.jaxrs.async;

import java.net.URL;
import java.util.concurrent.TimeUnit;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.test.integration.common.HttpRequest;
import org.jboss.as.test.integration.jaxrs.packaging.war.WebXml;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

/**
 * Tests the Jakarta RESTful Web Services async response functionality
 *
 * @author Stuart Douglas
 */
@RunWith(Arquillian.class)
@RunAsClient
public class JaxrsAsyncTestCase {

    @Deployment(testable = false)
    public static Archive<?> deploy() {
        WebArchive war = ShrinkWrap.create(WebArchive.class,"jaxrsnoap.war");
        war.addPackage(HttpRequest.class.getPackage());
        war.addPackage(JaxrsAsyncTestCase.class.getPackage());
        war.addAsWebInfResource(WebXml.get("<servlet-mapping>\n" +
                "        <servlet-name>jakarta.ws.rs.core.Application</servlet-name>\n" +
                "        <url-pattern>/myjaxrs/*</url-pattern>\n" +
                "    </servlet-mapping>\n" +
                "\n"),"web.xml");
        return war;
    }
    @ArquillianResource
    private URL url;

    private String performCall(String urlPattern) throws Exception {
        return HttpRequest.get(url + urlPattern, 10, TimeUnit.SECONDS);
    }

    @Test
    public void testJaxRsWithNoApplication() throws Exception {
        String result = performCall("myjaxrs/async/basic");
        assertEquals("basic", result);
    }


}
