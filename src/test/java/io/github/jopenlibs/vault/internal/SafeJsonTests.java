package io.github.jopenlibs.vault.internal;

import io.github.jopenlibs.vault.api.Logical.logicalOperations;
import io.github.jopenlibs.vault.response.AuthResponse;
import io.github.jopenlibs.vault.response.LogicalResponse;
import io.github.jopenlibs.vault.rest.RestResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SafeJsonTests {

    /** Depth of the payload in the vulnerability report, accepted by Vault before 1.20. */
    private static final int REPORTED_DEPTH = 9998;

    private static String nestedArrays(final int depth) {
        return "[".repeat(depth) + "]".repeat(depth);
    }

    private static String nestedObjects(final int depth) {
        return "{\"a\":".repeat(depth) + "1" + "}".repeat(depth);
    }

    @Test
    public void testArraysAtLimitAccepted() {
        final var value = SafeJson.parse(nestedArrays(SafeJson.MAX_NESTING_DEPTH));
        assertTrue(value.isArray());
    }

    @Test
    public void testObjectsAtLimitAccepted() {
        final var value = SafeJson.parse(nestedObjects(SafeJson.MAX_NESTING_DEPTH));
        assertTrue(value.isObject());
    }

    @Test
    public void testArraysAboveLimitRejected() {
        final var input = nestedArrays(SafeJson.MAX_NESTING_DEPTH + 1);
        final var e = assertThrows(JsonNestingException.class, () -> SafeJson.parse(input));
        assertEquals("JSON nested deeper than 500 levels at offset 500", e.getMessage());
    }

    @Test
    public void testObjectsAboveLimitRejected() {
        final var input = nestedObjects(SafeJson.MAX_NESTING_DEPTH + 1);
        assertThrows(JsonNestingException.class, () -> SafeJson.parse(input));
    }

    @Test
    public void testMixedNestingRejected() {
        final var input = "{\"a\":[".repeat(SafeJson.MAX_NESTING_DEPTH) + "]}".repeat(
                SafeJson.MAX_NESTING_DEPTH);
        assertThrows(JsonNestingException.class, () -> SafeJson.parse(input));
    }

    @Test
    public void testDepthIsNotCumulative() {
        final var sibling = nestedArrays(SafeJson.MAX_NESTING_DEPTH - 1);
        final var input = "[" + sibling + "," + sibling + "," + sibling + "]";
        assertTrue(SafeJson.parse(input).isArray());
    }

    @Test
    public void testBracketsInStringsIgnored() {
        final var input = "{\"key\":\"" + "[{".repeat(SafeJson.MAX_NESTING_DEPTH * 2) + "\"}";
        assertEquals("[{".repeat(SafeJson.MAX_NESTING_DEPTH * 2),
                SafeJson.parse(input).asObject().getString("key", null));
    }

    @Test
    public void testEscapedQuotesInStrings() {
        // The escaped quote does not end the string, so the brackets after it are still ignored
        final var input = "[\"\\\"" + "[".repeat(SafeJson.MAX_NESTING_DEPTH + 1) + "\"]";
        assertEquals("\"" + "[".repeat(SafeJson.MAX_NESTING_DEPTH + 1),
                SafeJson.parse(input).asArray().get(0).asString());
    }

    @Test
    public void testEscapedBackslashEndsString() {
        // "\\" is a complete string, so the brackets after it count
        final var input = "[\"\\\\\"," + nestedArrays(SafeJson.MAX_NESTING_DEPTH) + "]";
        assertThrows(JsonNestingException.class, () -> SafeJson.parse(input));
    }

    @Test
    public void testNullLeftToParser() {
        assertThrows(NullPointerException.class, () -> SafeJson.parse(null));
    }

    @Test
    public void testDeeplyNestedAuthResponseIgnored() {
        final var body = "{\"auth\":{\"client_token\":\"t\",\"metadata\":"
                + nestedArrays(REPORTED_DEPTH) + "}}";
        final var response = new AuthResponse(
                new RestResponse(200, "application/json", body.getBytes(StandardCharsets.UTF_8)),
                0);
        assertNull(response.getAuthClientToken());
    }

    /**
     * The scenario of the vulnerability report: a deeply nested value read through
     * {@code Logical.read()} must not kill the reading thread, even with a small stack.
     */
    @Test
    public void testDeeplyNestedLogicalResponseOnSmallStack() throws InterruptedException {
        final var body = "{\"data\":{\"data\":{\"note\":\"looks-normal\",\"payload\":"
                + nestedArrays(REPORTED_DEPTH) + "}}}";
        final var restResponse = new RestResponse(200, "application/json",
                body.getBytes(StandardCharsets.UTF_8));
        final var result = new AtomicReference<LogicalResponse>();
        final var error = new AtomicReference<Throwable>();

        final var thread = new Thread(null,
                () -> result.set(new LogicalResponse(restResponse, 0, logicalOperations.readV2)),
                "small-stack-reader", 256 * 1024);
        thread.setUncaughtExceptionHandler((t, e) -> error.set(e));
        thread.start();
        thread.join();

        assertNull(error.get());
        assertTrue(result.get().getData().isEmpty());
    }
}
