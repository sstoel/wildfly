package org.jboss.as.test.integration.jca.statistics;

import org.jboss.logging.Logger;

import jakarta.annotation.Resource;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;

@SuppressWarnings("serial")
@WebServlet(name = "sleep", urlPatterns = {"/sleep/"}, loadOnStartup = 1)
public class SleepServlet extends HttpServlet {

    private Logger LOGGER = Logger.getLogger("SleepServlet");

    @Resource(lookup = "java:jboss/datasources/ExampleDS")
    private DataSource dataSource;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Connection con = null;

        try {
            dataSource.getConnection();

            LOGGER.debug("## SLEEP START");
            Thread.sleep(3000L);
            LOGGER.debug("## SLEEP END");
        } catch (Exception e) {
            LOGGER.debug("Exception occured " + e.getMessage());
        } finally {
            try { con.close(); } catch (Exception ee) {}
        }

        response.setContentType("text/plain");
        final PrintWriter writer = response.getWriter();
        writer.write("Servlet result OK");
        writer.close();
    }
}
