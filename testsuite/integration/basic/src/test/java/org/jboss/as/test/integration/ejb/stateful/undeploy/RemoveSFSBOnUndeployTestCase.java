/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.ejb.stateful.undeploy;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.URL;
import java.util.Hashtable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jboss.logging.Logger;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.test.integration.common.HttpRequest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;


@RunWith(Arquillian.class)
@RunAsClient
public class RemoveSFSBOnUndeployTestCase {

    private static final Logger log = Logger.getLogger(RemoveSFSBOnUndeployTestCase.class.getName());

    @ArquillianResource
    Deployer deployer;

    private static Context context;

    @BeforeClass
    public static void beforeClass() throws Exception {
        final Hashtable props = new Hashtable();
        props.put(Context.URL_PKG_PREFIXES, "org.jboss.ejb.client.naming");
        context = new InitialContext(props);
    }

    @Deployment(name = "remote", testable = false)
    public static WebArchive createRemoteTestArchive() {
        return ShrinkWrap.create(WebArchive.class, "remote.war")
                .addClasses(TestServlet.class);
    }

    @Deployment(name="ejb", managed = false, testable = false)
    public static JavaArchive createMainTestArchive() {
        return ShrinkWrap.create(JavaArchive.class, "ejb.jar")
                .addClasses(TestSfsb.class, TestSfsbRemote.class)
                .addAsManifestResource(new StringAsset("Dependencies: deployment.remote.war\n"), "MANIFEST.MF");

    }
    @Test
    public void testSfsbDestroyedOnUndeploy(
            @ArquillianResource @OperateOnDeployment("remote") URL url) throws IOException,
            ExecutionException, TimeoutException, NamingException {
        deployer.deploy("ejb");
        try {
            final TestSfsbRemote localEcho = (TestSfsbRemote) context.lookup("ejb:/ejb/" + TestSfsb.class.getSimpleName() + "!" + TestSfsbRemote.class.getName()+"?stateful");
            localEcho.invoke();
            assertEquals("PostConstruct", HttpRequest.get(url + "/test", 10, TimeUnit.SECONDS));
            assertEquals("invoke", HttpRequest.get(url + "/test", 10, TimeUnit.SECONDS));
        }finally {
            deployer.undeploy("ejb");
        }
        assertEquals("PreDestroy", HttpRequest.get(url + "/test", 10, TimeUnit.SECONDS));

    }
}
