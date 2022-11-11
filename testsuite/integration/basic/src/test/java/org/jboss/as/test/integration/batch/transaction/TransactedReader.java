/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.integration.batch.transaction;

import jakarta.batch.api.BatchProperty;
import jakarta.batch.api.chunk.AbstractItemReader;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.jboss.logging.Logger;

@Named
public class TransactedReader extends AbstractItemReader {
    private static final Logger logger = Logger.getLogger(TransactedReader.class);

    @Inject
    @BatchProperty(name = "job.timeout")
    private int timeout;

    @Inject
    private TransactedService transactedService;

    @Override
    public Object readItem() throws Exception {
        // one can check the log to verify which batch thread is been used to
        // run this step or partition
        logger.info("About to read item, job.timeout: " + timeout);
        transactedService.query(timeout);
        return null;
    }

}
