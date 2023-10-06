/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.jpa.hibernate;

import java.io.IOException;
import java.io.InputStream;

import org.hibernate.boot.archive.spi.ArchiveException;
import org.hibernate.boot.archive.spi.InputStreamAccess;
import org.jboss.vfs.VirtualFile;


/**
 * InputStreamAccess provides Hibernate with lazy, on-demand access to InputStreams for the various
 * types of resources found during archive scanning.
 *
 * @author Steve Ebersole
 */
public class VirtualFileInputStreamAccess implements InputStreamAccess {
    private final String name;
    private final VirtualFile virtualFile;

    public VirtualFileInputStreamAccess(String name, VirtualFile virtualFile) {
        this.name = name;
        this.virtualFile = virtualFile;
    }

    @Override
    public String getStreamName() {
        return name;
    }

    @Override
    public InputStream accessInputStream() {
        try {
            return virtualFile.openStream();
        }
        catch (IOException e) {
            throw new ArchiveException( "Unable to open VirtualFile-based InputStream", e );
        }
    }

}
