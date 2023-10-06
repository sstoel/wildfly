/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jipijapa;

import static org.jboss.logging.Logger.Level.WARN;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

/**
 * JipiJapa integration layer message range is 20200-20299
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @author Scott Marlow
 */
@MessageLogger(projectCode = "JIPI")
public interface JipiLogger extends BasicLogger {

    /**
     * A logger with the category {@code org.jboss.jpa}.
     */
    JipiLogger JPA_LOGGER = Logger.getMessageLogger(JipiLogger.class, "org.jipijapa");


    /**
     * warn that the entity class could not be loaded with the
     * {@link jakarta.persistence.spi.PersistenceUnitInfo#getClassLoader()}.
     *
     * @param cause     the cause of the error.
     * @param className the entity class name.
     */
    @LogMessage(level = WARN)
    @Message(id = 20200, value = "Could not load entity class '%s', ignoring this error and continuing with application deployment")
    void cannotLoadEntityClass(@Cause Throwable cause, String className);

    /**
     * Creates an exception indicating the input stream reference cannot be changed.
     *
     * @return an {@link IllegalArgumentException} for the error.
     */
    @Message(id = 20201, value = "Cannot change input stream reference.")
    IllegalArgumentException cannotChangeInputStream();

    /**
     * Creates an exception indicating the parameter, likely a collection, is empty.
     *
     * @param parameterName the parameter name.
     * @return an {@link IllegalArgumentException} for the error.
     */
    @Message(id = 20202, value = "Parameter %s is empty")
    IllegalArgumentException emptyParameter(String parameterName);

    /**
     * Creates an exception indicating the persistence unit metadata likely because thread local was not set.
     *
     * @return a {@link RuntimeException} for the error.
     */
    @Message(id = 20203, value = "Missing PersistenceUnitMetadata (thread local wasn't set)")
    RuntimeException missingPersistenceUnitMetadata();

    /**
     * Creates an exception indicating the method is not yet implemented.
     *
     * @return a {@link RuntimeException} for the error.
     */
    @Message(id = 20204, value = "Not yet implemented")
    RuntimeException notYetImplemented();

    /**
     * Creates an exception indicating the variable is {@code null}.
     *
     * @param varName the variable name.
     * @return an {@link IllegalArgumentException} for the error.
     */
    @Message(id = 20205, value = "Parameter %s is null")
    IllegalArgumentException nullVar(String varName);

    /**
     * Could not open VFS stream
     *
     * @param cause the cause of the error.
     * @param name of VFS file
     * @return a {@link RuntimeException} for the error.
     */
    @Message(id = 20250, value = "Unable to open VirtualFile-based InputStream %s")
    RuntimeException cannotOpenVFSStream(@Cause Throwable cause, String name);

    /**
     * URI format is incorrect, which results in a syntax error
     *
     * @return an {@link IllegalArgumentException} for the error.
     */
    @Message(id = 20251, value = "URI syntax error")
    IllegalArgumentException uriSyntaxException(@Cause Throwable cause);

    /**
     * warn that the 2nd is not integrated
     *
     * @param scopedPuName identifies the app specific persistence unit name
     */
    @LogMessage(level = WARN)
    @Message(id = 20252, value = "second level cache not integrated - %s")
    void cannotUseSecondLevelCache(String scopedPuName);

}
