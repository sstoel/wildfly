/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.ejb.remote.async.classloading;

import java.io.IOException;

import org.junit.Assert;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.arquillian.api.ContainerResource;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test if Future can be returned as Async call
 *
 * @author baranowb
 */
@RunWith(Arquillian.class)
@RunAsClient
public class AsyncFutureTestCase {

    public static final String DEPLOYMENT_UNIT_JAR = "ejbjar";
    public static final String DEPLOYMENT_NAME_JAR = DEPLOYMENT_UNIT_JAR + ".jar";
    public static final String DEPLOYMENT_UNIT_EAR = "wildName";
    public static final String DEPLOYMENT_NAME_EAR = DEPLOYMENT_UNIT_EAR + ".ear";

    public static final String DEPLOYMENT_UNIT_WAR = "war-with-attitude";
    public static final String DEPLOYMENT_NAME_WAR = DEPLOYMENT_UNIT_WAR + ".war";

    public static final String DEPLOYMENT_NAME_COMMON_JAR = "common.jar";

    private static HttpClient HTTP_CLIENT;

    @BeforeClass
    public static void beforeClass() throws Exception {
        HTTP_CLIENT = new DefaultHttpClient();
    }

    @AfterClass
    public static void afterClass() {
        HTTP_CLIENT = null;
    }

    @ContainerResource
    private ManagementClient managementClient;

    @Deployment(name = DEPLOYMENT_UNIT_EAR, order = 0)
    public static Archive<EnterpriseArchive> createEAR() throws Exception {
        final JavaArchive jar = ShrinkWrap.create(JavaArchive.class, DEPLOYMENT_NAME_JAR);
        jar.addClass(AsyncRemoteEJB.class);

        final EnterpriseArchive ear = ShrinkWrap.create(EnterpriseArchive.class, DEPLOYMENT_NAME_EAR);
        ear.addAsModule(jar);
        ear.addAsLibraries(createCommonJar());
        return ear;
    }

    @Deployment(name = DEPLOYMENT_UNIT_WAR, order = 1)
    public static Archive<WebArchive> createWAR() throws Exception {
        final WebArchive war = ShrinkWrap.create(WebArchive.class, DEPLOYMENT_NAME_WAR);
        war.addClass(AsyncReceiverServlet.class);
        war.addAsLibraries(createCommonJar());
        return war;
    }

    private static JavaArchive createCommonJar() {
        final JavaArchive jar = ShrinkWrap.create(JavaArchive.class, DEPLOYMENT_NAME_COMMON_JAR);
        jar.addClasses(ReturnObject.class, AsyncRemote.class);
        return jar;
    }

    @Test
    public void testAsyncResultInServlet() throws Exception {
        final String requestURL = managementClient.getWebUri() + "/" + DEPLOYMENT_UNIT_WAR + "/x";
        final HttpGet get = new HttpGet(requestURL);
        final HttpResponse response = HTTP_CLIENT.execute(get);
        // consume!!
        consume(response);
        Assert.assertEquals(requestURL + ">" + response.getStatusLine().getReasonPhrase(), 200, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testAsyncNullResultInServlet() throws Exception {
        final String requestURL = managementClient.getWebUri() + "/" + DEPLOYMENT_UNIT_WAR + "/x?null=true";
        final HttpGet get = new HttpGet(requestURL);
        final HttpResponse response = HTTP_CLIENT.execute(get);
        // consume!!
        consume(response);
        Assert.assertEquals(requestURL + ">" + response.getStatusLine().getReasonPhrase(), 200, response.getStatusLine().getStatusCode());
    }

    private void consume(final HttpResponse httpResponse) {
        final HttpEntity httpEntity = httpResponse.getEntity();
        if (httpEntity != null) {
            try {
                EntityUtils.consume(httpEntity);
            } catch (IOException e) {
                //dont care?
                e.printStackTrace();
            }
        }
    }
}
