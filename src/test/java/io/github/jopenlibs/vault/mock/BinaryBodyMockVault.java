package io.github.jopenlibs.vault.mock;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import org.eclipse.jetty.server.Request;

/**
 * <p>This class is used to mock out a Vault server in unit tests verifying that response bodies
 * are passed through byte for byte. It responds to every request with HTTP 200 and the
 * pre-determined bytes, sent as <code>application/octet-stream</code>.</p>
 */
public class BinaryBodyMockVault extends MockVault {

    private final byte[] mockBody;

    public BinaryBodyMockVault(final byte[] mockBody) {
        this.mockBody = Arrays.copyOf(mockBody, mockBody.length);
    }

    @Override
    public void handle(
            final String target,
            final Request baseRequest,
            final HttpServletRequest request,
            final HttpServletResponse response
    ) throws IOException {
        response.setContentType("application/octet-stream");
        response.setStatus(200);
        baseRequest.setHandled(true);
        response.getOutputStream().write(mockBody);
    }
}
