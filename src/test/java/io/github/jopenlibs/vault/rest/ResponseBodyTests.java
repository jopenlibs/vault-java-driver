package io.github.jopenlibs.vault.rest;

import io.github.jopenlibs.vault.VaultTestUtils;
import io.github.jopenlibs.vault.mock.BinaryBodyMockVault;
import org.eclipse.jetty.server.Server;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Unit tests verifying that the REST client returns response bodies unaltered.
 */
public class ResponseBodyTests {

    private static final byte[] BINARY_BODY = new byte[256];

    static {
        for (int i = 0; i < BINARY_BODY.length; i++) {
            BINARY_BODY[i] = (byte) i;
        }
    }

    private Server server;

    @Before
    public void startServer() throws Exception {
        this.server = VaultTestUtils.initHttpMockVault(new BinaryBodyMockVault(BINARY_BODY));
        this.server.start();
    }

    @After
    public void stopServer() throws Exception {
        VaultTestUtils.shutdownMockVault(this.server);
    }

    /**
     * A body that is not valid UTF-8 (e.g. a DER certificate) must be returned byte for byte.
     */
    @Test
    public void testBinaryBodyIsNotAltered() throws RestException {
        final var restResponse = new Rest()
                .url("http://127.0.0.1:8999/")
                .get();
        assertEquals(200, restResponse.getStatus());
        assertEquals("application/octet-stream", restResponse.getMimeType());
        assertArrayEquals(BINARY_BODY, restResponse.getBody());
    }
}
