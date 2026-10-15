/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.web.host;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.undertow.predicate.Predicate;

/**
 * @author Stuart Douglas
 */
public class WebDeploymentBuilder {

    private ClassLoader classLoader;
    private String contextRoot;
    private File documentRoot;
    private ApplicationContextWrapper applicationContextWrapper;
    private boolean protectedPathsServed;
    private final List<ServletBuilder> servlets = new ArrayList<>();
    private final Map<String,String> mimeTypes = new HashMap<>();
    public final List<Predicate> allowRequestPredicates = new ArrayList<>();

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public WebDeploymentBuilder setClassLoader(final ClassLoader classLoader) {
        this.classLoader = classLoader;
        return this;
    }

    public void addServlet(final ServletBuilder servlet) {
        servlets.add(servlet);
    }

    public List<ServletBuilder> getServlets() {
        return Collections.unmodifiableList(servlets);
    }

    public String getContextRoot() {
        return contextRoot;
    }

    public WebDeploymentBuilder setContextRoot(final String contextRoot) {
        this.contextRoot = contextRoot;
        return this;
    }

    public File getDocumentRoot() {
        return documentRoot;
    }

    public WebDeploymentBuilder setDocumentRoot(final File documentRoot) {
        this.documentRoot = documentRoot;
        return this;
    }

    public WebDeploymentBuilder addAllowedRequestPredicate(Predicate predicate) {
        allowRequestPredicates.add(predicate);
        return this;
    }

    public List<Predicate> getAllowRequestPredicates() {
        return Collections.unmodifiableList(allowRequestPredicates);
    }

    public Map<String, String> getMimeTypes() {
        return mimeTypes;
    }

    public void addMimeMapping(String type,String mapping){
        mimeTypes.put(type,mapping);
    }

    /**
     * Whether the paths below WEB-INF and META-INF are dispatched to the servlets. A web application does not serve
     * them (Servlet 10.5); a deployment that does not come from a web archive, e.g. a registration of the OSGi Http
     * Service, has no such directories and can serve every path below its context path.
     */
    public boolean isProtectedPathsServed() {
        return protectedPathsServed;
    }

    public WebDeploymentBuilder setProtectedPathsServed(final boolean protectedPathsServed) {
        this.protectedPathsServed = protectedPathsServed;
        return this;
    }

    public ApplicationContextWrapper getApplicationContextWrapper() {
        return applicationContextWrapper;
    }

    public void setApplicationContextWrapper(final ApplicationContextWrapper applicationContextWrapper) {
        this.applicationContextWrapper = applicationContextWrapper;
    }
}
