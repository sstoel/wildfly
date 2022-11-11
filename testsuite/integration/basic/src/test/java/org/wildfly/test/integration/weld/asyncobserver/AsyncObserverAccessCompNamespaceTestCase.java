/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.wildfly.test.integration.weld.asyncobserver;

import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
public class AsyncObserverAccessCompNamespaceTestCase {

   @Deployment
   public static Archive<?> getDeployment() {
      JavaArchive lib = ShrinkWrap.create(JavaArchive.class)
         .addClasses(TestObserver.class, AsyncObserverAccessCompNamespaceTestCase.class);
      return lib;
   }

   @Inject
   Event<TestObserver.TestEvent> testEvents;

   @Test
   public void testSendingEvent() throws Exception {
      CompletableFuture<TestObserver.TestEvent> future = testEvents.fireAsync(new TestObserver.TestEvent()).toCompletableFuture();

      // just a sanity check - should throw exception on future.get in case of problems
      Assert.assertNotNull(future.get(500, TimeUnit.MILLISECONDS));
   }
}
