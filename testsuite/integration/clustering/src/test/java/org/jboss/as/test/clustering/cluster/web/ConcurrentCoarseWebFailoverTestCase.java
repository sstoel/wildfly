/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.clustering.cluster.web;

import org.infinispan.transaction.TransactionMode;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.test.clustering.ClusterTestUtil;
import org.jboss.as.test.clustering.single.web.Mutable;
import org.jboss.as.test.clustering.single.web.SimpleServlet;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;

@ServerSetup(ConcurrentSessionServerSetup.class)
public class ConcurrentCoarseWebFailoverTestCase extends AbstractWebFailoverTestCase {

    private static final String MODULE_NAME = ConcurrentCoarseWebFailoverTestCase.class.getSimpleName();
    private static final String DEPLOYMENT_NAME = MODULE_NAME + ".war";

    public ConcurrentCoarseWebFailoverTestCase() {
        super(DEPLOYMENT_NAME, TransactionMode.NON_TRANSACTIONAL);
    }

    @Deployment(name = DEPLOYMENT_1, managed = false, testable = false)
    @TargetsContainer(NODE_1)
    public static Archive<?> deployment1() {
        return getDeployment();
    }

    @Deployment(name = DEPLOYMENT_2, managed = false, testable = false)
    @TargetsContainer(NODE_2)
    public static Archive<?> deployment2() {
        return getDeployment();
    }

    @Deployment(name = DEPLOYMENT_3, managed = false, testable = false)
    @TargetsContainer(NODE_3)
    public static Archive<?> deployment3() {
        return getDeployment();
    }

    private static Archive<?> getDeployment() {
        WebArchive war = ShrinkWrap.create(WebArchive.class, DEPLOYMENT_NAME);
        war.addClasses(SimpleServlet.class, Mutable.class);
        ClusterTestUtil.addTopologyListenerDependencies(war);
        // Take web.xml from the managed test.
        war.setWebXML(DistributableTestCase.class.getPackage(), "web.xml");
        war.addAsWebInfResource(DistributableTestCase.class.getPackage(), "jboss-all_concurrent_coarse.xml", "jboss-all.xml");
        return war;
    }
}
