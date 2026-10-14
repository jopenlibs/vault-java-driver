package io.github.jopenlibs.vault;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

public class RetryPolicyTests {

    @Test
    public void testValues() {
        final var policy = new RetryPolicy(5, 1000);
        assertEquals(5, policy.getMaxRetries());
        assertEquals(1000, policy.getRetryIntervalMilliseconds());
    }

    @Test
    public void testZeroValuesAllowed() {
        final var policy = new RetryPolicy(0, 0);
        assertEquals(0, policy.getMaxRetries());
        assertEquals(0, policy.getRetryIntervalMilliseconds());
    }

    @Test
    public void testNegativeMaxRetriesRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RetryPolicy(-1, 1000));
    }

    @Test
    public void testNegativeIntervalRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RetryPolicy(5, -1));
    }

    @Test
    public void testEqualsAndHashCode() {
        final var policy = new RetryPolicy(5, 1000);
        final var same = new RetryPolicy(5, 1000);
        assertEquals(policy, policy);
        assertEquals(policy, same);
        assertEquals(policy.hashCode(), same.hashCode());
        assertNotEquals(policy, new RetryPolicy(4, 1000));
        assertNotEquals(policy, new RetryPolicy(5, 999));
        assertNotEquals(policy, null);
        assertNotEquals(policy, "RetryPolicy");
    }

    @Test
    public void testToString() {
        assertEquals("RetryPolicy{maxRetries=5, retryIntervalMilliseconds=1000}",
                new RetryPolicy(5, 1000).toString());
    }
}
