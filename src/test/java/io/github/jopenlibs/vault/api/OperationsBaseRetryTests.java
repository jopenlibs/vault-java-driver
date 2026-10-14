package io.github.jopenlibs.vault.api;

import io.github.jopenlibs.vault.VaultException;
import io.github.jopenlibs.vault.rest.Rest;
import io.github.jopenlibs.vault.rest.RestException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for the retry logic shared by all API operations.
 */
public class OperationsBaseRetryTests {

    @After
    public void clearInterrupt() {
        Thread.interrupted();
    }

    @Test
    public void testTransientHttpStatusesAreRetryable() {
        for (final int status : new int[]{408, 412, 429, 500, 502, 503}) {
            assertTrue("HTTP " + status,
                    OperationsBase.isRetryable(new VaultException("error", status)));
        }
    }

    @Test
    public void testPermanentHttpStatusesAreNotRetryable() {
        for (final int status : new int[]{200, 204, 400, 401, 403, 404, 405}) {
            assertFalse("HTTP " + status,
                    OperationsBase.isRetryable(new VaultException("error", status)));
        }
    }

    @Test
    public void testIoErrorsAreRetryable() {
        assertTrue(OperationsBase.isRetryable(connectionRefused()));
        assertTrue(OperationsBase.isRetryable(new IOException("reset")));
    }

    @Test
    public void testOtherErrorsAreNotRetryable() throws Exception {
        assertFalse(OperationsBase.isRetryable(new VaultException("MountPayload is missing")));
        assertFalse(OperationsBase.isRetryable(new RuntimeException("unexpected payload")));
        assertFalse(OperationsBase.isRetryable(new IllegalArgumentException()));
        try {
            new Rest().get();
            fail("Expected RestException for missing URL");
        } catch (RestException e) {
            assertFalse(OperationsBase.isRetryable(e));
        }
    }

    @Test
    public void testRetriesTransientErrorUntilSuccess() throws Exception {
        final var calls = new AtomicInteger();
        final String result = OperationsBase.retry(attempt -> {
            if (calls.incrementAndGet() < 3) {
                throw new VaultException("unavailable", 503);
            }
            return "ok:" + attempt;
        }, 5, 1);

        assertEquals("ok:2", result);
        assertEquals(3, calls.get());
    }

    @Test
    public void testDoesNotRetryPermanentError() {
        final var calls = new AtomicInteger();
        final var error = new VaultException("permission denied", 403);
        try {
            OperationsBase.retry(attempt -> {
                calls.incrementAndGet();
                throw error;
            }, 5, 1);
            fail("Expected VaultException");
        } catch (VaultException e) {
            assertSame(error, e);
        }
        assertEquals(1, calls.get());
    }

    @Test
    public void testStopsRetryingWhenInterrupted() {
        final var calls = new AtomicInteger();
        final var error = new VaultException("unavailable", 503);
        Thread.currentThread().interrupt();
        try {
            OperationsBase.retry(attempt -> {
                calls.incrementAndGet();
                throw error;
            }, 5, 10_000);
            fail("Expected VaultException");
        } catch (VaultException e) {
            assertSame(error, e);
        }
        assertEquals(1, calls.get());
        assertTrue(Thread.currentThread().isInterrupted());
    }

    private static RestException connectionRefused() {
        try {
            new Rest().url("http://127.0.0.1:1/").connectTimeoutSeconds(1).get();
        } catch (RestException e) {
            return e;
        }
        throw new AssertionError("Expected connection to port 1 to fail");
    }
}
