/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.xerces.ws.unit;

import java.io.IOException;
import java.net.URL;

import javax.xml.namespace.QName;
import jakarta.xml.ws.Service;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.test.integration.xerces.XercesUsageServlet;
import org.jboss.as.test.integration.xerces.ws.XercesUsageWSEndpoint;
import org.jboss.as.test.integration.xerces.ws.XercesUsageWebService;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.PomEquippedResolveStage;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests that packaging a xerces jar within a web application containing a webservice implementation, doesn't break the
 * functioning of the webservice.
 *
 * @author Jaikiran Pai
 */
@RunWith(Arquillian.class)
@RunAsClient
public class XercesUsageInWebServiceTestCase {

    private static final String WEBSERVICE_WEB_APP_CONTEXT = "xerces-webservice-webapp";

    private static final Logger logger = Logger.getLogger(XercesUsageInWebServiceTestCase.class);

    @ArquillianResource
    private URL url;

    /**
     * Creates a .war file, containing a webservice implementation and also packages the xerces jar within the
     * web application's .war/WEB-INF/lib
     *
     * @return
     */
    @Deployment(name = "webservice-app-with-xerces", testable = false)
    public static WebArchive createWebServiceDeployment() throws IOException {
        final WebArchive war = ShrinkWrap.create(WebArchive.class, WEBSERVICE_WEB_APP_CONTEXT + ".war");
        war.addClasses(XercesUsageWebService.class, XercesUsageWSEndpoint.class);
        // add a web.xml containing the webservice mapping as a servlet
        war.addAsWebInfResource(XercesUsageServlet.class.getPackage(), "xerces-webservice-web.xml", "web.xml");
        // add a dummy xml to parse
        war.addAsResource(XercesUsageServlet.class.getPackage(), "dummy.xml", "dummy.xml");

        // add the xerces jar in the .war/WEB-INF/lib
        final PomEquippedResolveStage resolver = Maven.resolver().loadPomFromFile("pom.xml");
        war.addAsLibraries(resolver.resolve("xerces:xercesImpl:2.12.1").withoutTransitivity().asSingleFile());


        return war;
    }

    /**
     * Test that the webservice invocation works fine
     *
     * @throws Exception
     */
    @OperateOnDeployment("webservice-app-with-xerces")
    @Test
    public void testXercesUsageInWebService() throws Exception {

        final QName serviceName = new QName("org.jboss.as.test.integration.xerces.ws", "XercesUsageWebService");
        final URL wsdlURL = new URL(url.toExternalForm() + "XercesUsageWebService?wsdl");
        final Service service = Service.create(wsdlURL, serviceName);
        final XercesUsageWSEndpoint port = service.getPort(XercesUsageWSEndpoint.class);
        final String xml = "dummy.xml";
        final String result = port.parseUsingXerces(xml);
        Assert.assertEquals("Unexpected return message from webservice", XercesUsageWebService.SUCCESS_MESSAGE, result);
    }


}
