/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.jsf.deployment;

import java.util.List;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests for the JSFModuleIdFactory
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2012 Red Hat Inc.
 */
public class JSFModuleIdFactoryTestCase {

    private static final String API_MODULE = "jakarta.faces.api";
    private static final String IMPL_MODULE = "jakarta.faces.impl";
    private static final String INJECTION_MODULE = "org.jboss.as.jsf-injection";

    private static final JSFModuleIdFactory factory = JSFModuleIdFactory.getInstance();

    @Test
    public void getActiveJSFVersionsTest() {
        List<String> versions = factory.getActiveJSFVersions();
        Assert.assertEquals(3, versions.size());
        Assert.assertTrue(versions.contains("main"));
        Assert.assertTrue(versions.contains("myfaces"));
    }

    @Test
    public void computeSlotTest() {
        Assert.assertEquals("main", factory.computeSlot("main"));
        Assert.assertEquals("main", factory.computeSlot(null));
        Assert.assertEquals("main", factory.computeSlot(JsfVersionMarker.JSF_4_0));
        Assert.assertEquals("myfaces2", factory.computeSlot("myfaces2"));
    }

    @Test
    public void validSlotTest() {
        Assert.assertTrue(factory.isValidJSFSlot("main"));
        Assert.assertTrue(factory.isValidJSFSlot("myfaces"));
        Assert.assertTrue(factory.isValidJSFSlot(JsfVersionMarker.JSF_4_0));
        Assert.assertFalse(factory.isValidJSFSlot(JsfVersionMarker.WAR_BUNDLES_JSF_IMPL));
        Assert.assertFalse(factory.isValidJSFSlot("bogus"));
        Assert.assertFalse(factory.isValidJSFSlot("bogus2"));
   }

    @Test
    @Ignore("Depends on https://issues.redhat.com/browse/WFLY-17405")
    public void modIdsTest() {
        Assert.assertEquals(API_MODULE, factory.getApiModId("main").getName());
        Assert.assertEquals("main", factory.getApiModId("main").getSlot());
        Assert.assertEquals(IMPL_MODULE, factory.getImplModId("main").getName());
        Assert.assertEquals("main", factory.getImplModId("main").getSlot());
        Assert.assertEquals(INJECTION_MODULE, factory.getInjectionModId("main").getName());
        Assert.assertEquals("main", factory.getInjectionModId("main").getSlot());

        Assert.assertEquals(API_MODULE, factory.getApiModId("myfaces").getName());
        Assert.assertEquals("myfaces", factory.getApiModId("myfaces").getSlot());
        Assert.assertEquals(IMPL_MODULE, factory.getImplModId("myfaces").getName());
        Assert.assertEquals("myfaces", factory.getImplModId("myfaces").getSlot());
        Assert.assertEquals(INJECTION_MODULE, factory.getInjectionModId("myfaces").getName());
        Assert.assertEquals("myfaces", factory.getInjectionModId("myfaces").getSlot());

        Assert.assertEquals(API_MODULE, factory.getApiModId("myfaces2").getName());
        Assert.assertEquals("myfaces2", factory.getApiModId("myfaces2").getSlot());
        Assert.assertEquals(IMPL_MODULE, factory.getImplModId("myfaces2").getName());
        Assert.assertEquals("myfaces2", factory.getImplModId("myfaces2").getSlot());
        Assert.assertEquals(INJECTION_MODULE, factory.getInjectionModId("myfaces2").getName());
        Assert.assertEquals("myfaces2", factory.getInjectionModId("myfaces2").getSlot());
    }
}
