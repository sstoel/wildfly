/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.web.cache.session;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.Test;
import org.wildfly.clustering.ee.Batch;
import org.wildfly.clustering.ee.Batcher;
import org.wildfly.clustering.ee.cache.ConcurrentManager;
import org.wildfly.clustering.ee.cache.SimpleManager;
import org.wildfly.clustering.web.cache.session.attributes.SessionAttributes;
import org.wildfly.clustering.web.session.ImmutableSession;
import org.wildfly.clustering.web.session.Session;
import org.wildfly.clustering.web.session.SessionManager;
import org.wildfly.clustering.web.session.SessionMetaData;

/**
 * Unit test for {@link ConcurrentSessionManager}.
 * @author Paul Ferraro
 */
public class ConcurrentSessionManagerTestCase {

    @SuppressWarnings("unchecked")
    @Test
    public void findSession() {
        SessionManager<Void, Batch> manager = mock(SessionManager.class);
        SessionManager<Void, Batch> subject = new ConcurrentSessionManager<>(manager, ConcurrentManager::new);
        Session<Void> expected1 = mock(Session.class);
        Session<Void> expected2 = mock(Session.class);
        String id = "foo";
        SessionMetaData metaData1 = mock(SessionMetaData.class);
        SessionAttributes attributes1 = mock(SessionAttributes.class);
        SessionMetaData metaData2 = mock(SessionMetaData.class);
        SessionAttributes attributes2 = mock(SessionAttributes.class);

        when(manager.findSession(id)).thenReturn(expected1, expected2);
        when(expected1.getId()).thenReturn(id);
        when(expected1.isValid()).thenReturn(true);
        when(expected1.getAttributes()).thenReturn(attributes1);
        when(expected1.getMetaData()).thenReturn(metaData1);
        when(expected2.getId()).thenReturn(id);
        when(expected2.isValid()).thenReturn(true);
        when(expected2.getAttributes()).thenReturn(attributes2);
        when(expected2.getMetaData()).thenReturn(metaData2);

        try (Session<Void> session1 = subject.findSession(id)) {
            assertNotNull(session1);
            assertSame(id, session1.getId());
            assertSame(metaData1, session1.getMetaData());
            assertSame(attributes1, session1.getAttributes());

            try (Session<Void> session2 = subject.findSession(id)) {
                assertNotNull(session2);
                // Should return the same session without invoking the manager
                assertSame(session1, session2);
            }

            // Should not trigger Session.close() yet
            verify(expected1, never()).close();
        }

        verify(expected1).close();

        // Should use second session instance
        try (Session<Void> session = subject.findSession(id)) {
            assertNotNull(session);
            assertSame(id, session.getId());
            assertSame(metaData2, session.getMetaData());
            assertSame(attributes2, session.getAttributes());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void findInvalidSession() {
        SessionManager<Void, Batch> manager = mock(SessionManager.class);
        SessionManager<Void, Batch> subject = new ConcurrentSessionManager<>(manager, ConcurrentManager::new);
        Session<Void> expected1 = mock(Session.class);
        String id = "foo";
        SessionMetaData metaData1 = mock(SessionMetaData.class);
        SessionAttributes attributes1 = mock(SessionAttributes.class);

        when(manager.findSession(id)).thenReturn(expected1, (Session<Void>) null);
        when(expected1.getId()).thenReturn(id);
        when(expected1.isValid()).thenReturn(true);
        when(expected1.getAttributes()).thenReturn(attributes1);
        when(expected1.getMetaData()).thenReturn(metaData1);

        try (Session<Void> session1 = subject.findSession(id)) {
            assertNotNull(session1);
            assertSame(id, session1.getId());
            assertSame(metaData1, session1.getMetaData());
            assertSame(attributes1, session1.getAttributes());

            session1.invalidate();

            verify(expected1).invalidate();
            verify(expected1).close();

            Session<Void> session2 = subject.findSession(id);
            assertNull(session2);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void createSession() {
        SessionManager<Void, Batch> manager = mock(SessionManager.class);
        SessionManager<Void, Batch> subject = new ConcurrentSessionManager<>(manager, ConcurrentManager::new);
        Session<Void> expected1 = mock(Session.class);
        Session<Void> expected2 = mock(Session.class);
        String id = "foo";
        SessionMetaData metaData1 = mock(SessionMetaData.class);
        SessionAttributes attributes1 = mock(SessionAttributes.class);
        SessionMetaData metaData2 = mock(SessionMetaData.class);
        SessionAttributes attributes2 = mock(SessionAttributes.class);

        when(manager.createSession(id)).thenReturn(expected1, expected2);
        when(expected1.getId()).thenReturn(id);
        when(expected1.isValid()).thenReturn(true);
        when(expected1.getAttributes()).thenReturn(attributes1);
        when(expected1.getMetaData()).thenReturn(metaData1);
        when(expected2.getId()).thenReturn(id);
        when(expected2.isValid()).thenReturn(true);
        when(expected2.getAttributes()).thenReturn(attributes2);
        when(expected2.getMetaData()).thenReturn(metaData2);

        try (Session<Void> session1 = subject.createSession(id)) {
            assertNotNull(session1);
            assertSame(id, session1.getId());
            assertSame(metaData1, session1.getMetaData());
            assertSame(attributes1, session1.getAttributes());

            try (Session<Void> session2 = subject.findSession(id)) {
                assertNotNull(session2);
                // Should return the same session without invoking the manager
                assertSame(session1, session2);
            }

            // Should not trigger Session.close() yet
            verify(expected1, never()).close();
        }

        verify(expected1).close();

        // Should use second session instance
        try (Session<Void> session = subject.createSession(id)) {
            assertNotNull(session);
            assertSame(id, session.getId());
            assertSame(metaData2, session.getMetaData());
            assertSame(attributes2, session.getAttributes());
        }
    }

    @Test
    public void getIdentifierFactory() {
        SessionManager<Void, Batch> manager = mock(SessionManager.class);
        SessionManager<Void, Batch> subject = new ConcurrentSessionManager<>(manager, SimpleManager::new);
        Supplier<String> expected = mock(Supplier.class);

        when(manager.getIdentifierFactory()).thenReturn(expected);

        Supplier<String> result = subject.getIdentifierFactory();

        assertSame(expected, result);
    }

    @Test
    public void start() {
        SessionManager<Void, Batch> manager = mock(SessionManager.class);
        SessionManager<Void, Batch> subject = new ConcurrentSessionManager<>(manager, SimpleManager::new);

        subject.start();

        verify(manager).start();
    }

    @Test
    public void stop() {
        SessionManager<Void, Batch> manager = mock(SessionManager.class);
        SessionManager<Void, Batch> subject = new ConcurrentSessionManager<>(manager, SimpleManager::new);

        subject.stop();

        verify(manager).stop();
    }

    @Test
    public void getActiveSessionCount() {
        SessionManager<Void, Batch> manager = mock(SessionManager.class);
        SessionManager<Void, Batch> subject = new ConcurrentSessionManager<>(manager, SimpleManager::new);
        long expected = 1000L;

        when(manager.getActiveSessionCount()).thenReturn(expected);

        long result = subject.getActiveSessionCount();

        assertEquals(expected, result);
    }

    @Test
    public void getBatcher() {
        SessionManager<Void, Batch> manager = mock(SessionManager.class);
        SessionManager<Void, Batch> subject = new ConcurrentSessionManager<>(manager, SimpleManager::new);
        Batcher<Batch> expected = mock(Batcher.class);

        when(manager.getBatcher()).thenReturn(expected);

        Batcher<Batch> result = subject.getBatcher();

        assertSame(expected, result);
    }

    @Test
    public void getActiveSessions() {
        SessionManager<Void, Batch> manager = mock(SessionManager.class);
        SessionManager<Void, Batch> subject = new ConcurrentSessionManager<>(manager, SimpleManager::new);
        Set<String> expected = Collections.singleton("foo");

        when(manager.getActiveSessions()).thenReturn(expected);

        Set<String> result = subject.getActiveSessions();

        assertSame(expected, result);
    }

    @Test
    public void getLocalSessions() {
        SessionManager<Void, Batch> manager = mock(SessionManager.class);
        SessionManager<Void, Batch> subject = new ConcurrentSessionManager<>(manager, SimpleManager::new);
        Set<String> expected = Collections.singleton("foo");

        when(manager.getLocalSessions()).thenReturn(expected);

        Set<String> result = subject.getLocalSessions();

        assertSame(expected, result);
    }

    @Test
    public void readSession() {
        SessionManager<Void, Batch> manager = mock(SessionManager.class);
        SessionManager<Void, Batch> subject = new ConcurrentSessionManager<>(manager, SimpleManager::new);
        ImmutableSession expected = mock(ImmutableSession.class);
        String id = "foo";

        when(manager.readSession(id)).thenReturn(expected);

        ImmutableSession result = subject.readSession(id);

        assertSame(expected, result);
    }

    @Test
    public void getStopTimeout() {
        SessionManager<Void, Batch> manager = mock(SessionManager.class);
        SessionManager<Void, Batch> subject = new ConcurrentSessionManager<>(manager, SimpleManager::new);
        Duration expected = Duration.ofMinutes(1);

        when(manager.getStopTimeout()).thenReturn(expected);

        Duration result = subject.getStopTimeout();

        assertSame(expected, result);
    }
}
