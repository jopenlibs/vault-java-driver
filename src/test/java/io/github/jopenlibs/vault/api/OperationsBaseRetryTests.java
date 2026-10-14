package io.github.jopenlibs.vault.api;

import io.github.jopenlibs.vault.RetryPolicy;
import io.github.jopenlibs.vault.Vault;
import io.github.jopenlibs.vault.VaultConfig;
import io.github.jopenlibs.vault.VaultException;
import io.github.jopenlibs.vault.rest.Rest;
import io.github.jopenlibs.vault.rest.RestException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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

    @Test
    public void testNullPolicyUsesConfigRetries() throws Exception {
        final var config = configWithRetries(2, 1);
        final var operations = new TestOperations(config);
        assertNull(operations.retryPolicy);
        assertEquals(2, operations.getMaxRetries());
        assertEquals(1, operations.getRetryIntervalMilliseconds());
        assertEquals(3, operations.countAttempts());
    }

    @Test
    public void testNullPolicyReadsConfigOnEveryCall() throws Exception {
        final var config = configWithRetries(1, 1);
        final var operations = new TestOperations(config, null);
        assertEquals(2, operations.countAttempts());

        Vault.create(config).withRetries(3, 1);
        assertEquals(3, operations.getMaxRetries());
        assertEquals(4, operations.countAttempts());
    }

    @Test
    public void testNullPolicyKeepsNegativeConfigValues() throws Exception {
        final var config = configWithRetries(-1, -5);
        final var operations = new TestOperations(config);
        assertEquals(-1, operations.getMaxRetries());
        assertEquals(-5, operations.getRetryIntervalMilliseconds());
        assertEquals(1, operations.countAttempts());
    }

    @Test
    public void testPolicyOverridesConfigRetries() throws Exception {
        final var config = configWithRetries(5, 1000);
        final var policy = new RetryPolicy(1, 2);
        final var operations = new TestOperations(config, policy);
        assertSame(policy, operations.retryPolicy);
        assertEquals(1, operations.getMaxRetries());
        assertEquals(2, operations.getRetryIntervalMilliseconds());
        assertEquals(2, operations.countAttempts());

        Vault.create(config).withRetries(3, 1);
        assertEquals(2, operations.countAttempts());
        assertEquals(3, config.getMaxRetries());
    }

    @Test
    public void testZeroPolicyDisablesRetries() throws Exception {
        final var operations = new TestOperations(configWithRetries(5, 1), new RetryPolicy(0, 0));
        assertEquals(1, operations.countAttempts());
    }

    private static VaultConfig configWithRetries(final int maxRetries,
            final int retryIntervalMilliseconds) throws VaultException {
        final var config = new VaultConfig().address("http://127.0.0.1:8999")
                .token("mock_token").engineVersion(1).build();
        Vault.create(config).withRetries(maxRetries, retryIntervalMilliseconds);
        return config;
    }

    private static final class TestOperations extends OperationsBase {

        TestOperations(final VaultConfig config) {
            super(config);
        }

        TestOperations(final VaultConfig config, final RetryPolicy retryPolicy) {
            super(config, retryPolicy);
        }

        /**
         * @return The number of attempts made for an operation that always fails with a transient
         * error
         */
        int countAttempts() {
            final var calls = new AtomicInteger();
            try {
                retry(attempt -> {
                    calls.incrementAndGet();
                    throw new VaultException("unavailable", 503);
                });
                fail("Expected VaultException");
            } catch (VaultException e) {
                assertEquals(503, e.getHttpStatusCode());
            }
            return calls.get();
        }
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
