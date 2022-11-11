package org.jboss.as.test.integration.jpa.initializeinorder;

import java.io.IOException;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebServlet;

/**
 * @author Scott Marlow
 */
@WebServlet(name = "MyServlet", urlPatterns = {"/test"}, loadOnStartup = 1)
public class MyServlet implements Servlet {

    @Inject CdiJpaInjectingBean bean;

    @PostConstruct
    public void postConstruct() {
        //we wait a second, to make sure that the EJB is actually waiting for us to start, and it is not just
        //the normal random init order
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        TestState.addInitOrder(MyServlet.class.getSimpleName());
        if (bean != null) {
            TestState.setJpaInjectingBeanAvailable();
            if (bean.entityManagerFactory() != null) {
                TestState.setEntityManagerFactoryAvailable();
            }
            if (bean.entityManager() != null) {
                TestState.setEntityManagerAvailable();
            }
        }
    }

    @Override
    public void init(final ServletConfig config) throws ServletException {

    }

    @Override
    public ServletConfig getServletConfig() {
        return null;
    }

    @Override
    public void service(final ServletRequest req, final ServletResponse res) throws ServletException, IOException {

    }

    @Override
    public String getServletInfo() {
        return null;
    }

    @Override
    public void destroy() {

    }
}
