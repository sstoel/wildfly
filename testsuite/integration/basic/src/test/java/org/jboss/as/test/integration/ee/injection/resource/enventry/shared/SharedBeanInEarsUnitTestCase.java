/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.ee.injection.resource.enventry.shared;

import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Migrated from EJB3 testsuite [JBQA-5483] from ejbthree454.
 * Test EJBs of the same name and code that are deployed in different ears.
 *
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 */
@RunWith(Arquillian.class)
@RunAsClient
public class SharedBeanInEarsUnitTestCase {
    private static final Logger log = Logger.getLogger(SharedBeanInEarsUnitTestCase.class);

    private static String APP_A = "ear-a";
    private static String MOD_A_SHARED = "jar-a-shared";
    private static String MOD_A = "jar-a";
    private static String APP_B = "ear-b";
    private static String MOD_B_SHARED = "jar-b-shared";
    private static String MOD_B = "jar-b";

    @Deployment(name = "a", testable = false, managed = true)
    public static Archive<?> deployA() {
        EnterpriseArchive ear = ShrinkWrap.create(EnterpriseArchive.class, APP_A + ".ear");
        JavaArchive jarA = ShrinkWrap.create(JavaArchive.class, MOD_A + ".jar");
        jarA.addClasses(AEJB.class, AEJBBean.class);

        JavaArchive jarAshared = ShrinkWrap.create(JavaArchive.class, MOD_A_SHARED + ".jar");
        jarAshared.addClasses(Shared.class, SharedBean.class);
        jarAshared.addAsManifestResource(SharedBeanInEarsUnitTestCase.class.getPackage(), "ejb-jar-a.xml", "ejb-jar.xml");

        ear.addAsModule(jarA);
        ear.addAsModule(jarAshared);
        ear.addAsManifestResource(SharedBeanInEarsUnitTestCase.class.getPackage(), "application-a.xml", "application.xml");
        return ear;
    }

    @Deployment(name = "b", testable = false, managed = true)
    public static Archive<?> deployB() {
        EnterpriseArchive ear = ShrinkWrap.create(EnterpriseArchive.class, APP_B + ".ear");
        JavaArchive jarB = ShrinkWrap.create(JavaArchive.class, MOD_B + ".jar");
        jarB.addClasses(BEJB.class, BEJBBean.class);

        JavaArchive jarBshared = ShrinkWrap.create(JavaArchive.class, MOD_B_SHARED + ".jar");
        jarBshared.addClasses(Shared.class, SharedBean.class);
        jarBshared.addAsManifestResource(SharedBeanInEarsUnitTestCase.class.getPackage(), "ejb-jar-b.xml", "ejb-jar.xml");

        ear.addAsModule(jarB);
        ear.addAsModule(jarBshared);
        ear.addAsManifestResource(SharedBeanInEarsUnitTestCase.class.getPackage(), "application-b.xml", "application.xml");
        return ear;
    }

    private InitialContext getInitialContext() throws NamingException {
        final Hashtable<String, String> jndiProperties = new Hashtable<String, String>();
        jndiProperties.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.as.naming.InitialContextFactory");
        jndiProperties.put(Context.URL_PKG_PREFIXES, "org.jboss.ejb.client.naming");
        return new InitialContext(jndiProperties);
    }

    @Test
    public void testScopedClassLoaders() throws Exception {
        AEJB aremote = (AEJB) getInitialContext().lookup("ejb:" + APP_A + "/" + MOD_A + "//" + AEJBBean.class.getSimpleName() + "!" + AEJB.class.getName());
        Assert.assertEquals("A", aremote.doit());

        BEJB bremote = (BEJB) getInitialContext().lookup("ejb:" + APP_B + "/" + MOD_B + "//" + BEJBBean.class.getSimpleName() + "!" + BEJB.class.getName());
        Assert.assertEquals("B", bremote.doit());
    }
}
