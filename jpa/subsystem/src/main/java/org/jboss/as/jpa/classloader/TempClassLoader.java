/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.jpa.classloader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.jboss.modules.ConcurrentClassLoader;

/**
 * Return a new instance of a ClassLoader that the may be used to temporarily load any classes,
 * resources, or open URLs.  None of the classes loaded by this class loader will be visible to
 * application components.
 * <p/>
 * TempClassLoader is suitable for implementing jakarta.persistence.spi.PersistenceUnitInfo.getNewTempClassLoader()
 * <p/>
 *
 * @author Scott Marlow
 * @author Antti Laisi
 */
public class TempClassLoader extends ConcurrentClassLoader {

    public static final String STARTS_WITH_JAVAX = "javax.";
    public static final String STARTS_WITH_JAVA = "java.";
    public static final String STARTS_WITH_JAKARTA = "jakarta.";
    private final ClassLoader delegate;
    private static final String MANIFEST_MF = "META-INF" + File.separatorChar + "MANIFEST.MF";

    static {
        try {
            ClassLoader.registerAsParallelCapable();
        } catch (Throwable ignored) {}
    }

    TempClassLoader(final ClassLoader delegate) {
        super((ConcurrentClassLoader) null);
        this.delegate = delegate;
    }

    @Override
    protected Class<?> findClass(String name, boolean exportsOnly, boolean resolve) throws ClassNotFoundException {

        Class<?> loaded = findLoadedClass(name);
        if (loaded != null) {
            return loaded;
        }

        // jakarta.persistence classes must be loaded by module classloader, otherwise
        // the persistence provider can't read Jakarta Persistence annotations with reflection
        if (name.startsWith(STARTS_WITH_JAKARTA) ||
                name.startsWith(STARTS_WITH_JAVAX) ||
                name.startsWith(STARTS_WITH_JAVA)) {
            return Class.forName(name, resolve, delegate);
        }

        InputStream resource = delegate.getResourceAsStream(name.replace('.', '/') + ".class");
        if (resource == null) {
            throw new ClassNotFoundException(name);
        }

        // Ensure that the package is loaded
        final int lastIdx = name.lastIndexOf('.');
        if (lastIdx != -1) {
            // there's a package name; get the Package for it
            final String packageName = name.substring(0, lastIdx);
            synchronized (this) {
                Package pkg = findLoadedPackage(packageName);
                if (pkg == null) {
                    Manifest manifest = readManifestFile();
                    if (manifest != null) {
                        final Attributes mainAttribute = manifest.getMainAttributes();
                        final Attributes entryAttribute = manifest.getAttributes(packageName);
                        URL url =
                                "true".equals(getDefinedAttribute(Attributes.Name.SEALED, entryAttribute, mainAttribute))
                                        ? delegate.getResource(name.replace('.', '/') + ".class") : null;
                        definePackage(
                                packageName,
                                getDefinedAttribute(Attributes.Name.SPECIFICATION_TITLE, entryAttribute, mainAttribute),
                                getDefinedAttribute(Attributes.Name.SPECIFICATION_VERSION, entryAttribute, mainAttribute),
                                getDefinedAttribute(Attributes.Name.SPECIFICATION_VENDOR, entryAttribute, mainAttribute),
                                getDefinedAttribute(Attributes.Name.IMPLEMENTATION_TITLE, entryAttribute, mainAttribute),
                                getDefinedAttribute(Attributes.Name.IMPLEMENTATION_VERSION, entryAttribute, mainAttribute),
                                getDefinedAttribute(Attributes.Name.IMPLEMENTATION_VENDOR, entryAttribute, mainAttribute),
                                url
                                );
                    }
                    else {
                        definePackage(packageName, null, null, null, null, null, null, null);
                    }

                }
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[1024];
            for (int i = 0; (i = resource.read(buffer, 0, buffer.length)) != -1; ) {
                baos.write(buffer, 0, i);
            }
            buffer = baos.toByteArray();
            return defineClass(name, buffer, 0, buffer.length);
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        } finally {
            try {
                resource.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    @Override
    protected URL findResource(String name, boolean exportsOnly) {
        return delegate.getResource(name);
    }

    @Override
    protected Enumeration<URL> findResources(String name, boolean exportsOnly) throws IOException {
        return delegate.getResources(name);
    }

    @Override
    protected InputStream findResourceAsStream(String name, boolean exportsOnly) {
        return delegate.getResourceAsStream(name);
    }

    /** {@inheritDoc} */
    @Override
    protected final Package definePackage(final String name, final String specTitle, final String specVersion, final String specVendor, final String implTitle, final String implVersion, final String implVendor, final URL sealBase) throws IllegalArgumentException {
        return super.definePackage(name, specTitle, specVersion, specVendor, implTitle, implVersion, implVendor, sealBase);
    }

    private Manifest readManifestFile() {
        InputStream resource = null;
        try {
            resource = delegate.getResourceAsStream(MANIFEST_MF);
            return resource != null ? new Manifest(resource) : null;
        } catch (IOException e) {
            return null;
        }
        finally {
            if ( resource != null) {
                try {
                    resource.close();
                } catch (IOException ignored) {

                }
            }
        }
    }

    private static String getDefinedAttribute(Attributes.Name name, Attributes entryAttribute, Attributes mainAttribute) {
        final String value = entryAttribute == null ? null : entryAttribute.getValue(name);
        return value == null ? mainAttribute == null ? null : mainAttribute.getValue(name) : value;
    }

}
