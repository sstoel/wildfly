/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.undertow;

import java.util.StringTokenizer;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import jakarta.servlet.DispatcherType;

import io.undertow.UndertowMessages;
import io.undertow.server.Connectors;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.api.ServletDispatcher;
import io.undertow.servlet.core.ServletBlockingHttpExchange;
import io.undertow.servlet.handlers.ServletPathMatch;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.servlet.spec.HttpServletRequestImpl;
import io.undertow.servlet.spec.HttpServletResponseImpl;
import io.undertow.servlet.spec.ServletContextImpl;

/**
 * Dispatches the requests for paths below WEB-INF and META-INF to the servlets of a deployment that does not come from
 * a web archive, e.g. a registration of the OSGi Http Service. The servlet initial handler answers these requests with
 * 404, which protects the directories of a web archive (Servlet 10.5); such a deployment has no such directories, its
 * servlets get every path below the context path. Other requests go to the servlet initial handler.
 *
 * The handler wraps the servlet initial handler. For a protected path it does what the servlet initial handler does,
 * without the check of the path.
 */
final class ProtectedPathDispatchHandler implements HttpHandler {

    private static final String META_INF = "META-INF";
    private static final String WEB_INF = "WEB-INF";

    private final HttpHandler next;
    private final Supplier<Deployment> deployment;

    ProtectedPathDispatchHandler(final HttpHandler next, final Supplier<Deployment> deployment) {
        this.next = next;
        this.deployment = deployment;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        final String path = exchange.getRelativePath();
        final Deployment deployment = this.deployment.get();
        if (deployment == null || !isProtectedPath(path)) {
            next.handleRequest(exchange);
            return;
        }

        final long contentLength = exchange.getRequestContentLength();
        final ServletPathMatch info = deployment.getServletPaths().getServletHandlerByPath(path);
        if (info.getType() == ServletPathMatch.Type.REWRITE) {
            exchange.setRelativePath(info.getRewriteLocation());
            exchange.setRequestPath(exchange.getResolvedPath() + info.getRewriteLocation());
        }
        final ServletContextImpl servletContext = deployment.getServletContext();
        final HttpServletResponseImpl response = new HttpServletResponseImpl(exchange, servletContext);
        final HttpServletRequestImpl request = new HttpServletRequestImpl(exchange, servletContext);
        final ServletRequestContext servletRequestContext = new ServletRequestContext(deployment, request, response, info);
        if (info.getServletChain().getManagedServlet().getMaxMultipartRequestSize() > 0 && exchange.isMultiPartExchange()) {
            exchange.setMaxEntitySize(info.getServletChain().getManagedServlet().getMaxMultipartRequestSize());
        }
        if (contentLength > 0 && exchange.getMaxEntitySize() > 0 && exchange.getMaxEntitySize() < contentLength && exchange.isResponseChannelAvailable()) {
            exchange.setPersistent(false);
            Connectors.terminateRequest(exchange);
            throw UndertowMessages.MESSAGES.requestEntityWasTooLarge(exchange.getMaxEntitySize());
        }
        exchange.putAttachment(ServletRequestContext.ATTACHMENT_KEY, servletRequestContext);

        exchange.startBlocking(new ServletBlockingHttpExchange(exchange));
        servletRequestContext.setServletPathMatch(info);

        Executor executor = info.getServletChain().getExecutor();
        if (executor == null) {
            executor = deployment.getExecutor();
        }
        final ServletDispatcher dispatcher = deployment.getServletDispatcher();
        final HttpHandler dispatchHandler = new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                dispatcher.dispatchToPath(exchange, info, DispatcherType.REQUEST);
            }
        };
        if (exchange.isInIoThread() || executor != null) {
            // either the exchange has not been dispatched yet, or we need to use a special executor
            exchange.dispatch(executor, dispatchHandler);
        } else {
            dispatchHandler.handleRequest(exchange);
        }
    }

    // The first segment of the path is WEB-INF or META-INF, ignoring case, as the servlet initial handler checks it
    static boolean isProtectedPath(final String path) {
        final StringTokenizer st = new StringTokenizer(path, "/\\", false);
        if (st.hasMoreTokens()) {
            final String subPath = st.nextToken();
            return META_INF.equalsIgnoreCase(subPath) || WEB_INF.equalsIgnoreCase(subPath);
        }
        return false;
    }
}
