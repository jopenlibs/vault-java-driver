package io.github.jopenlibs.vault.mock;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.eclipse.jetty.server.Request;

/**
 * <p>This class is used to mock out a Vault server in unit tests involving retry logic.  As it
 * extends Jetty's
 * <code>AbstractHandler</code>, it can be passed to an embedded Jetty server and respond to actual
 * (albeit localhost) HTTP requests.</p>
 *
 * <p>The basic usage pattern is as follows:</p>
 *
 * <ol>
 *     <li>
 *         <code>RetriesMockVault</code> responds with a specified HTTP status code to a designated number of requests (which
 *         can be zero).  This can be used to test retry logic.
 *     </li>
 *     <li>
 *         On subsequent HTTP requests, <code>RetriesMockVault</code> responds with a designated HTTP status code, and
 *         a designated response body.
 *     </li>
 * </ol>
 *
 * <p>Example usage:</p>
 *
 * <blockquote>
 * <pre>{@code
 * final Server server = new Server(8999);
 * server.setHandler( new RetriesMockVault(5, 200, "{\"data\":{\"value\":\"mock\"}}") );
 * server.start();
 *
 * final VaultConfig vaultConfig = new VaultConfig("http://127.0.0.1:8999", "mock_token");
 * final Vault vault = Vault.create(vaultConfig);
 * final LogicalResponse response = vault.withRetries(5, 100).logical().read("secret/hello");
 * assertEquals(5, response.getRetries());
 * assertEquals("mock", response.getData().get("value"));
 *
 * VaultTestUtils.shutdownMockVault(server);
 * }</pre>
 * </blockquote>
 */
public class RetriesMockVault extends MockVault {

    private final int mockStatus;
    private final String mockResponse;
    private final int failureStatus;
    private int failureCount;

    public RetriesMockVault(final int failureCount, final int mockStatus,
            final String mockResponse) {
        this(failureCount, 500, mockStatus, mockResponse);
    }

    public RetriesMockVault(final int failureCount, final int failureStatus,
            final int mockStatus, final String mockResponse) {
        this.failureCount = failureCount;
        this.failureStatus = failureStatus;
        this.mockStatus = mockStatus;
        this.mockResponse = mockResponse;
    }

    @Override
    public void handle(
            final String target,
            final Request baseRequest,
            final HttpServletRequest request,
            final HttpServletResponse response
    ) throws IOException {
        response.setContentType("application/json");
        baseRequest.setHandled(true);
        if (failureCount > 0) {
            failureCount = failureCount - 1;
            response.setStatus(failureStatus);
            System.out.println("RetriesMockVault is sending an HTTP " + failureStatus + " code, to trigger retry logic...");
        } else {
            System.out.println("RetriesMockVault is sending an HTTP " + mockStatus
                    + " code, with expected success payload...");
            response.setStatus(mockStatus);
            if (mockResponse != null) {
                response.getWriter().println(mockResponse);
            }
        }
    }
}
