/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.clustering.cluster.dispatcher.bean;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.wildfly.clustering.dispatcher.Command;
import org.wildfly.clustering.group.Node;

public class TestCommand implements Command<String, Node> {
    private static final long serialVersionUID = -3405593925871250676L;

    @Override
    public String execute(Node node) {
        try {
            // Ensure the thread context classloader of the command execution is correct
            Thread.currentThread().getContextClassLoader().loadClass(this.getClass().getName());
            // Ensure the correct naming context is set
            Context context = new InitialContext();
            try {
                context.lookup("java:comp/env/clustering/dispatcher");
            } finally {
                context.close();
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        } catch (NamingException e) {
            throw new IllegalStateException(e);
        }
        return node.getName();
    }
}
