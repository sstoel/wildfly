/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.jsf.phaselistener.injectiontarget.bean;

import java.net.URL;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.core.util.JacksonFeature;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Tomas Remes
 */
@RunWith(Arquillian.class)
@RunAsClient
public class InjectionBeanToPhaseListenerTest {

    @ArquillianResource
    URL url;

    @Deployment(testable = false)
    public static Archive<?> deploy() {
        final WebArchive war = ShrinkWrap.create(WebArchive.class, "jsfphaseListenerBean.war");
        war.addPackage(InjectionBeanToPhaseListenerTest.class.getPackage());
        war.addAsWebInfResource(InjectionBeanToPhaseListenerTest.class.getPackage(), "web.xml", "web.xml");
        war.addAsWebInfResource(InjectionBeanToPhaseListenerTest.class.getPackage(), "faces-config.xml", "faces-config.xml");
        war.addAsWebResource(InjectionBeanToPhaseListenerTest.class.getPackage(), "home.xhtml", "home.xhtml");
        war.addAsWebInfResource(new StringAsset("<beans bean-discovery-mode=\"all\"></beans>"), "beans.xml");
        return war;
    }

    @Test
    public void test() throws Exception {
        try (Client client = ClientBuilder.newClient().register(JacksonFeature.class)) {
            WebTarget target = client.target(url.toExternalForm() + "home.jsf");
            Response response = target.request().get();
            String value = response.getHeaderString("X-WildFly");
            Assert.assertNotNull(value);
            Assert.assertEquals(value, SimpleBean.MESSAGE);
        }
    }

}
