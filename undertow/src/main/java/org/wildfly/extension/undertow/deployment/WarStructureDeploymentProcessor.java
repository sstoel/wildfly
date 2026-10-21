/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.undertow.deployment;

import java.io.Closeable;
import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Manifest;

import io.undertow.util.FileUtils;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.ee.structure.DeploymentType;
import org.jboss.as.ee.structure.DeploymentTypeMarker;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.MountedDeploymentOverlay;
import org.jboss.as.server.deployment.PrivateSubDeploymentMarker;
import org.jboss.as.server.deployment.module.FilterSpecification;
import org.jboss.as.server.deployment.module.ModuleRootMarker;
import org.jboss.as.server.deployment.module.ModuleSpecification;
import org.jboss.as.server.deployment.module.MountHandle;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.as.server.deployment.module.TempFileProviderService;
import org.jboss.as.web.common.SharedTldsMetaDataBuilder;
import org.jboss.as.web.common.WarMetaData;
import org.jboss.modules.filter.PathFilters;
import org.jboss.modules.security.ImmediatePermissionFactory;
import org.jboss.vfs.VFS;
import org.jboss.vfs.VirtualFile;
import org.jboss.vfs.VirtualFileFilter;
import org.jboss.vfs.VisitorAttributes;
import org.jboss.vfs.util.SuffixMatchFilter;
import org.wildfly.extension.undertow.logging.UndertowLogger;

/**
 * Create and mount classpath entries in the .war deployment.
 *
 * @author Emanuel Muckenhuber
 * @author Thomas.Diesler@jboss.com
 */
public class WarStructureDeploymentProcessor implements DeploymentUnitProcessor {

    private static final String TEMP_DIR = "jboss.server.temp.dir";

    public static final String WEB_INF_LIB = "WEB-INF/lib";
    public static final String WEB_INF_CLASSES = "WEB-INF/classes";
    // The name prefix of the resource roots of the directories on the Bundle-ClassPath of an OSGi web application
    public static final String BUNDLE_CLASS_PATH_DIRECTORY_PREFIX = "Bundle-ClassPath:";
    public static final String META_INF = "META-INF";

    private static final String WEB_INF_EXTERNAL_MOUNTS = "WEB-INF/undertow-external-mounts.conf";

    public static final VirtualFileFilter DEFAULT_WEB_INF_LIB_FILTER = new SuffixMatchFilter(".jar", VisitorAttributes.DEFAULT);

    private final SharedTldsMetaDataBuilder sharedTldsMetaData;

    public WarStructureDeploymentProcessor(final SharedTldsMetaDataBuilder sharedTldsMetaData) {
        this.sharedTldsMetaData = sharedTldsMetaData;
    }

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        if (!DeploymentTypeMarker.isType(DeploymentType.WAR, deploymentUnit)) {
            return; // Skip non web deployments
        }

        final ResourceRoot deploymentResourceRoot = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT);

        final VirtualFile deploymentRoot = deploymentResourceRoot.getRoot();
        if (deploymentRoot == null) {
            return;
        }

        // set the child first behaviour
        final ModuleSpecification moduleSpecification = deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION);
        if (moduleSpecification == null) {
            return;
        }
        moduleSpecification.setPrivateModule(true);

        // other sub deployments should not have access to classes in the war module
        PrivateSubDeploymentMarker.mark(deploymentUnit);

        // OSGi WebApp deployments (WAB) use the deployment root when it is on their Bundle-ClassPath, also with
        // WEB-INF/classes (ee/05). Without the header they use it if they don't use WEB-INF/classes already
        final Set<String> wabClassPath = getDeclaredBundleClassPath(deploymentUnit);
        final boolean wabRoot = deploymentUnit.hasAttachment(Attachments.OSGI_MANIFEST)
                && (wabClassPath != null ? wabClassPath.contains(".") : !deploymentRoot.getChild(WEB_INF_CLASSES).exists());
        if (wabRoot) {
            // The index of the root has the classes of the root only: WEB-INF and the other entries of the Bundle-ClassPath
            // are resource roots of their own, or not part of the web application
            deploymentResourceRoot.addToAttachmentList(Attachments.INDEX_IGNORE_PATHS, "WEB-INF");
            if (wabClassPath != null) {
                for (String entry : wabClassPath) {
                    if (!entry.equals(".")) {
                        deploymentResourceRoot.addToAttachmentList(Attachments.INDEX_IGNORE_PATHS, entry);
                    }
                }
            }
        } else {
            // we do not want to index the resource root, only WEB-INF/classes and WEB-INF/lib
            deploymentResourceRoot.putAttachment(Attachments.INDEX_RESOURCE_ROOT, false);

            // Make sure the root does not end up in the module, only META-INF
            deploymentResourceRoot.getExportFilters().add(new FilterSpecification(PathFilters.getMetaInfFilter(), true));
            deploymentResourceRoot.getExportFilters().add(new FilterSpecification(PathFilters.getMetaInfSubdirectoriesFilter(), true));
            deploymentResourceRoot.getExportFilters().add(new FilterSpecification(PathFilters.acceptAll(), false));
            ModuleRootMarker.mark(deploymentResourceRoot, true);
        }

        // TODO: This needs to be ported to add additional resource roots the standard way
        final MountHandle mountHandle = deploymentResourceRoot.getMountHandle();
        try {
            // add standard resource roots, this should eventually replace ClassPathEntry
            final List<ResourceRoot> resourceRoots = createResourceRoots(deploymentRoot, deploymentUnit);
            for (ResourceRoot root : resourceRoots) {
                deploymentUnit.addToAttachmentList(Attachments.RESOURCE_ROOTS, root);
            }
        } catch (Exception e) {
            throw new DeploymentUnitProcessingException(e);
        }
        // Add the war metadata
        final WarMetaData warMetaData = new WarMetaData();
        deploymentUnit.putAttachment(WarMetaData.ATTACHMENT_KEY, warMetaData);

        String deploymentName;
        if(deploymentUnit.getParent() == null) {
            deploymentName = deploymentUnit.getName();
        } else {
            deploymentName = deploymentUnit.getParent().getName() + "." + deploymentUnit.getName();
        }

        PathManager pathManager = deploymentUnit.getAttachment(Attachments.PATH_MANAGER);

        File tempDir = new File(pathManager.getPathEntry(TEMP_DIR).resolvePath(), deploymentName);
        tempDir.mkdirs();
        warMetaData.setTempDir(tempDir);

        moduleSpecification.addPermissionFactory(new ImmediatePermissionFactory(new FilePermission(tempDir.getAbsolutePath() + File.separatorChar + "-", "read,write,delete")));

        // Add the shared TLDs metadata
        final TldsMetaData tldsMetaData = new TldsMetaData();
        tldsMetaData.setSharedTlds(sharedTldsMetaData);
        deploymentUnit.putAttachment(TldsMetaData.ATTACHMENT_KEY, tldsMetaData);

        processExternalMounts(deploymentUnit, deploymentRoot);
    }

    private void processExternalMounts(DeploymentUnit deploymentUnit, VirtualFile deploymentRoot) throws DeploymentUnitProcessingException {
        VirtualFile mounts = deploymentRoot.getChild(WEB_INF_EXTERNAL_MOUNTS);
        if(!mounts.exists()) {
            return;
        }
        try (InputStream data = mounts.openStream()) {
            String contents = FileUtils.readFile(data);
            String[] lines = contents.split("\n");
            for(String line : lines) {
                String trimmed = line;
                int commentIndex = trimmed.indexOf("#");
                if(commentIndex > -1) {
                    trimmed = trimmed.substring(0, commentIndex);
                }
                trimmed = trimmed.trim();
                if(trimmed.isEmpty()) {
                    continue;
                }
                File path = new File(trimmed);
                if(path.exists()) {
                    deploymentUnit.addToAttachmentList(UndertowAttachments.EXTERNAL_RESOURCES, path);
                } else {
                    throw UndertowLogger.ROOT_LOGGER.couldNotFindExternalPath(path);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create the resource roots for a .war deployment
     *
     *
     * @param deploymentRoot the deployment root
     * @return the resource roots
     * @throws java.io.IOException for any error
     */
    private List<ResourceRoot> createResourceRoots(final VirtualFile deploymentRoot, final DeploymentUnit deploymentUnit) throws IOException, DeploymentUnitProcessingException {
        final List<ResourceRoot> entries = new ArrayList<ResourceRoot>();
        // The web resources of an OSGi web application (WAB) are those on its Bundle-ClassPath (OSGi Enterprise 128.3.7)
        final Set<String> bundleClassPath = getBundleClassPath(deploymentUnit);
        // WEB-INF classes, of a WAB that declares its Bundle-ClassPath only when it is on it (ee/05)
        final Set<String> declaredClassPath = getDeclaredBundleClassPath(deploymentUnit);
        final VirtualFile webinfClasses = deploymentRoot.getChild(WEB_INF_CLASSES);
        if (webinfClasses.exists() && (declaredClassPath == null || declaredClassPath.contains(WEB_INF_CLASSES))) {
            final ResourceRoot webInfClassesRoot = new ResourceRoot(webinfClasses.getName(), webinfClasses, null);
            ModuleRootMarker.mark(webInfClassesRoot);
            entries.add(webInfClassesRoot);
        }
        // WEB-INF lib
        Map<String, MountedDeploymentOverlay> overlays = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_OVERLAY_LOCATIONS);
        final VirtualFile webinfLib = deploymentRoot.getChild(WEB_INF_LIB);
        if (webinfLib.exists()) {
            final List<VirtualFile> archives = webinfLib.getChildren(DEFAULT_WEB_INF_LIB_FILTER);
            for (final VirtualFile archive : archives) {
                try {

                    String relativeName = archive.getPathNameRelativeTo(deploymentRoot);
                    // A JAR that is not on the Bundle-ClassPath of a WAB is not part of the web application
                    if (bundleClassPath != null && !bundleClassPath.contains(relativeName)) {
                        continue;
                    }
                    MountedDeploymentOverlay overlay = overlays.get(relativeName);
                    Closeable closable = null;
                    if(overlay != null) {
                        overlay.remountAsZip(false);
                    } else if (archive.isFile()) {
                        closable = VFS.mountZip(archive.getPhysicalFile(), archive, TempFileProviderService.provider());
                    } else {
                        closable = null;
                    }
                    final ResourceRoot webInfArchiveRoot = new ResourceRoot(archive.getName(), archive, MountHandle.create(closable));
                    ModuleRootMarker.mark(webInfArchiveRoot);
                    entries.add(webInfArchiveRoot);
                } catch (IOException e) {
                    throw new DeploymentUnitProcessingException(UndertowLogger.ROOT_LOGGER.failToProcessWebInfLib(archive), e);
                }
            }
        }
        // The other JARs and directories on the Bundle-ClassPath of a WAB are web resources like those in WEB-INF
        if (bundleClassPath != null) {
            for (String entry : bundleClassPath) {
                if (entry.equals(".") || entry.equals(WEB_INF_CLASSES) || entry.startsWith(WEB_INF_LIB + "/")) {
                    continue;
                }
                final VirtualFile file = deploymentRoot.getChild(entry);
                if (!file.exists()) {
                    continue;
                }
                try {
                    final ResourceRoot root;
                    if (file.isFile()) {
                        Closeable closable = VFS.mountZip(file.getPhysicalFile(), file, TempFileProviderService.provider());
                        root = new ResourceRoot(file.getName(), file, MountHandle.create(closable));
                    } else {
                        root = new ResourceRoot(BUNDLE_CLASS_PATH_DIRECTORY_PREFIX + entry, file, null);
                    }
                    ModuleRootMarker.mark(root);
                    entries.add(root);
                } catch (IOException e) {
                    throw new DeploymentUnitProcessingException(UndertowLogger.ROOT_LOGGER.failToProcessWebInfLib(file), e);
                }
            }
        }
        return entries;
    }

    /**
     * The entries of the Bundle-ClassPath of an OSGi web application, null for other deployments
     */
    // The Bundle-ClassPath of a WAB that has the header, null otherwise
    private static Set<String> getDeclaredBundleClassPath(final DeploymentUnit deploymentUnit) {
        final Manifest manifest = deploymentUnit.getAttachment(Attachments.OSGI_MANIFEST);
        final String header = manifest != null ? manifest.getMainAttributes().getValue("Bundle-ClassPath") : null;
        return header == null || header.trim().isEmpty() ? null : getBundleClassPath(deploymentUnit);
    }

    private static Set<String> getBundleClassPath(final DeploymentUnit deploymentUnit) {
        final Manifest manifest = deploymentUnit.getAttachment(Attachments.OSGI_MANIFEST);
        if (manifest == null) {
            return null;
        }
        final Set<String> result = new LinkedHashSet<String>();
        final String header = manifest.getMainAttributes().getValue("Bundle-ClassPath");
        if (header == null || header.trim().isEmpty()) {
            result.add(".");
            return result;
        }
        for (String element : header.split(",")) {
            String path = element.split(";")[0].trim();
            while (path.startsWith("/")) {
                path = path.substring(1);
            }
            while (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            result.add(path.isEmpty() ? "." : path);
        }
        return result;
    }
}
