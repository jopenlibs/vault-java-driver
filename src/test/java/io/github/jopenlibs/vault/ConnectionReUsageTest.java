package io.github.jopenlibs.vault;

import io.github.jopenlibs.vault.api.Logical;
import io.github.jopenlibs.vault.response.LogicalResponse;
import io.github.jopenlibs.vault.mock.MockVault;
import java.net.Socket;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jetty.io.NetworkTrafficListener;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.NetworkTrafficServerConnector;
import org.eclipse.jetty.server.Server;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConnectionReUsageTest {

    private static final String TOKEN = "token";

    private final NetworkConnectionListener connectionListener = new NetworkConnectionListener();

    private Server vaultServerMock;

    @Before
    public void setUp() throws Exception {
        final MockVault mockVault = new MockVault(200, "{\"data\":{\"key\":\"value\"}}");
        vaultServerMock = initHttpMockVaultWithListener(mockVault, connectionListener);
        vaultServerMock.start();

        connectionListener.reset();
    }

    @After
    public void tearDown() throws Exception {
        if (vaultServerMock != null) {
            VaultTestUtils.shutdownMockVault(vaultServerMock);
        }
    }

    @Test
    public void readShouldReuseConnectionAfterSuccessfulRequestByHttp() throws Exception {
        int readNum = 10;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.of(10, ChronoUnit.SECONDS))
                .build();

        Logical vault = Vault.create(new VaultConfig()
                .httpClient(httpClient)
                .address("http://localhost:8999")
                .token(TOKEN)
                .readTimeout(10)
                .engineVersion(1)
                .build()).logical();

        for (int i = 0; i < readNum; i++) {
            LogicalResponse resp = vault.read("testing/p1");
            assertEquals("value", resp.getData().get("key"));
        }

        int closed = connectionListener.getClosed();
        int opened = connectionListener.getOpened();

        assertTrue("Too many connections opened: " + opened, opened <= (closed + 1));
    }

    private static class NetworkConnectionListener implements NetworkTrafficListener {

        private final AtomicInteger opened = new AtomicInteger();
        private final AtomicInteger closed = new AtomicInteger();

        @Override
        public void opened(Socket socket) {
            opened.incrementAndGet();
        }

        @Override
        public void closed(Socket socket) {
            closed.incrementAndGet();
        }

        @Override
        public void incoming(Socket socket, ByteBuffer byteBuffer) {
        }

        @Override
        public void outgoing(Socket socket, ByteBuffer byteBuffer) {
        }

        public int getOpened() {
            return opened.get();
        }

        public int getClosed() {
            return closed.get();
        }

        public void reset() {
            opened.set(0);
            closed.set(0);
        }
    }

    public Server initHttpMockVaultWithListener(final MockVault mock, NetworkTrafficListener listener) {
        final Server server = new Server();
        final HttpConfiguration http = new HttpConfiguration();

        NetworkTrafficServerConnector connector =
                new NetworkTrafficServerConnector(
                        server,
                        new HttpConnectionFactory(http));
        connector.setNetworkTrafficListener(listener);
        connector.setPort(8999);
        server.setConnectors(new Connector[]{connector});

        server.setHandler(mock);
        return server;
    }
}
