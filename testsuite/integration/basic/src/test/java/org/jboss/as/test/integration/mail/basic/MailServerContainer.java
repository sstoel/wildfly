/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.mail.basic;

import java.util.List;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

public class MailServerContainer extends GenericContainer<MailServerContainer> {

    public MailServerContainer(String confPath) {
        super(DockerImageName.parse("apache/james:demo-3.8.0"));
        this.setExposedPorts(List.of(25, 110));
        this.waitStrategy = Wait.forLogMessage(".*AddUser command executed sucessfully.*", 3);
        this.withCopyFileToContainer(MountableFile.forHostPath(confPath), "/root/conf/");
    }

    public String getMailServerHost() {
        return this.isRunning() ? this.getHost() : "localhost";
    }

    public Integer getSMTPMappedPort() {
        return this.isRunning() ? this.getMappedPort(25) : 1025;
    }

    public Integer getPOP3MappedPort() {
        return this.isRunning() ? this.getMappedPort(110) : 1110;
    }
}
