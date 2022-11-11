/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package org.jboss.as.test.integration.jaxrs.async;

import java.util.concurrent.TimeUnit;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * @author Stuart Douglas
 */
@Path("/async")
public class AsyncResource {
    @GET
    @Path("basic")
    @Produces("text/plain")
    public void getBasic(@Suspended final AsyncResponse response) throws Exception {
        response.setTimeout(1, TimeUnit.SECONDS);
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    Response jaxrs = Response.ok("basic").type(MediaType.TEXT_PLAIN).build();
                    response.resume(jaxrs);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        t.start();
    }
}
