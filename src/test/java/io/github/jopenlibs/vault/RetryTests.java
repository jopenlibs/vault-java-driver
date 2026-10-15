package io.github.jopenlibs.vault;

import io.github.jopenlibs.vault.api.OperationsBase;
import io.github.jopenlibs.vault.mock.RetriesMockVault;
import io.github.jopenlibs.vault.response.LogicalResponse;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

/**
 * <p>Unit tests for the Vault driver, having no dependency on an actual Vault server instance
 * being available.  The tests in this class relate to handling of retry logic.</p>
 */
public class RetryTests {

    @Test
    public void testRetries_Read() throws Exception {
        final var retriesMockVault = new RetriesMockVault(5, 200,
                "{\"lease_id\":\"12345\",\"renewable\":false,\"lease_duration\":10000,\"data\":{\"value\":\"mock\"}}");
        final var server = VaultTestUtils.initHttpMockVault(retriesMockVault);
        server.start();

        final var vaultConfig = new VaultConfig().address("http://127.0.0.1:8999")
                .token("mock_token").engineVersion(1).build();
        final var vault = Vault.create(vaultConfig);
        final LogicalResponse response = vault.withRetries(5, 100).logical().read("secret/hello");
        assertEquals(5, response.getRetries());
        assertEquals("mock", response.getData().get("value"));
        assertEquals("12345", response.getLeaseId());
        assertEquals(false, response.getRenewable());
        assertEquals(10000L, (long) response.getLeaseDuration());

        VaultTestUtils.shutdownMockVault(server);
    }

    @Test
    public void testRetries_Write() throws Exception {
        final var retriesMockVault = new RetriesMockVault(5, 204, null);
        final var server = VaultTestUtils.initHttpMockVault(retriesMockVault);
        server.start();

        final var vaultConfig = new VaultConfig().address("http://127.0.0.1:8999")
                .token("mock_token").build();
        final var vault = Vault.create(vaultConfig);
        final LogicalResponse response = vault.withRetries(5, 100).logical()
                .write("secret/hello", Map.of("value", "world"));
        assertEquals(5, response.getRetries());

        VaultTestUtils.shutdownMockVault(server);
    }

    @Test
    public void testRetries_Read_412() throws Exception {
        final var retriesMockVault = new RetriesMockVault(3, 412, 200,
                "{\"lease_id\":\"12345\",\"renewable\":false,\"lease_duration\":10000,\"data\":{\"value\":\"mock\"}}");
        final var server = VaultTestUtils.initHttpMockVault(retriesMockVault);
        server.start();

        final var vaultConfig = new VaultConfig().address("http://127.0.0.1:8999")
                .token("mock_token").engineVersion(1).build();
        final var vault = Vault.create(vaultConfig);
        final LogicalResponse response = vault.withRetries(5, 100).logical().read("secret/hello");
        assertEquals(3, response.getRetries());
        assertEquals("mock", response.getData().get("value"));

        VaultTestUtils.shutdownMockVault(server);
    }

    @Test
    public void testRetries_Write_412() throws Exception {
        final var retriesMockVault = new RetriesMockVault(3, 412, 204, null);
        final var server = VaultTestUtils.initHttpMockVault(retriesMockVault);
        server.start();

        final var vaultConfig = new VaultConfig().address("http://127.0.0.1:8999")
                .token("mock_token").build();
        final var vault = Vault.create(vaultConfig);
        final LogicalResponse response = vault.withRetries(5, 100).logical()
                .write("secret/hello", Map.of("value", "world"));
        assertEquals(3, response.getRetries());

        VaultTestUtils.shutdownMockVault(server);
    }

    @Test
    public void testNoRetries_Read_404() throws Exception {
        final var retriesMockVault = new RetriesMockVault(1, 404, 404,
                "{\"errors\":[\"Not found\"]}");
        final var server = VaultTestUtils.initHttpMockVault(retriesMockVault);
        server.start();

        final var vaultConfig = new VaultConfig().address("http://127.0.0.1:8999")
                .token("mock_token").engineVersion(1).build();
        final var vault = Vault.create(vaultConfig);
        final LogicalResponse response = vault.withRetries(5, 100).logical().read("secret/hello");
        assertEquals(0, response.getRetries());
        assertEquals(404, response.getRestResponse().getStatus());

        VaultTestUtils.shutdownMockVault(server);
    }

    @Test
    public void testNoRetries_Write_400() throws Exception {
        final var retriesMockVault = new RetriesMockVault(1, 400, 400,
                "{\"errors\":[\"Bad request\"]}");
        final var server = VaultTestUtils.initHttpMockVault(retriesMockVault);
        server.start();

        final var vaultConfig = new VaultConfig().address("http://127.0.0.1:8999")
                .token("mock_token").build();
        final var vault = Vault.create(vaultConfig);
        final LogicalResponse response = vault.withRetries(5, 100).logical()
                .write("secret/hello", Map.of("value", "world"));
        assertEquals(0, response.getRetries());
        assertEquals(400, response.getRestResponse().getStatus());

        VaultTestUtils.shutdownMockVault(server);
    }

    private static final String READ_RESPONSE =
            "{\"lease_id\":\"12345\",\"renewable\":false,\"lease_duration\":10000,\"data\":{\"value\":\"mock\"}}";

    @Test
    public void testRetrying_Read() throws Exception {
        final var server = VaultTestUtils.initHttpMockVault(
                new RetriesMockVault(5, 200, READ_RESPONSE));
        server.start();
        try {
            final var vault = Vault.create(mockConfig());
            final LogicalResponse response = vault.retrying(5, 10).logical().read("secret/hello");
            assertEquals(5, response.getRetries());
            assertEquals("mock", response.getData().get("value"));
        } finally {
            VaultTestUtils.shutdownMockVault(server);
        }
    }

    @Test
    public void testRetrying_LeavesOriginalUnchanged() throws Exception {
        final var server = VaultTestUtils.initHttpMockVault(
                new RetriesMockVault(1, 200, READ_RESPONSE));
        server.start();
        try {
            final var config = mockConfig();
            final var vault = Vault.create(config);
            assertNotSame(vault, vault.retrying(5, 10));
            assertEquals(0, config.getMaxRetries());
            assertEquals(0, config.getRetryIntervalMilliseconds());

            final var e = assertThrows(VaultException.class,
                    () -> vault.logical().read("secret/hello"));
            assertEquals(500, e.getHttpStatusCode());
        } finally {
            VaultTestUtils.shutdownMockVault(server);
        }
    }

    @Test
    public void testRetrying_OverridesConfigRetries() throws Exception {
        final var server = VaultTestUtils.initHttpMockVault(
                new RetriesMockVault(3, 200, READ_RESPONSE));
        server.start();
        try {
            final var vault = Vault.create(mockConfig());
            vault.withRetries(5, 10);
            // 1 retry is fewer than the 3 failures, while the config retries would be enough
            final var e = assertThrows(VaultException.class,
                    () -> vault.retrying(1, 10).logical().read("secret/hello"));
            assertEquals(500, e.getHttpStatusCode());
        } finally {
            VaultTestUtils.shutdownMockVault(server);
        }
    }

    @Test
    public void testRetrying_SysLeasesGetsPolicy() throws Exception {
        final var server = VaultTestUtils.initHttpMockVault(new RetriesMockVault(2, 204, null));
        server.start();
        try {
            final var vault = Vault.create(mockConfig());
            final var response = vault.retrying(2, 10).sys().leases().revoke("lease-id");
            assertEquals(2, response.getRetries());
        } finally {
            VaultTestUtils.shutdownMockVault(server);
        }
    }

    @Test
    public void testRetrying_SharesConfig() throws Exception {
        final var config = mockConfig();
        final var retrying = Vault.create(config).retrying(5, 10);
        assertSame(config, baseFieldOf(retrying.auth(), "config"));
        config.token("new_token");
        assertArrayEquals("new_token".toCharArray(),
                ((VaultConfig) baseFieldOf(retrying.logical(), "config")).getToken());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testRetrying_EveryOperationGetsPolicy() throws Exception {
        final var vault = Vault.create(mockConfig());
        final var policy = new RetryPolicy(5, 10);
        final var retrying = vault.retrying(5, 10);
        for (final OperationsBase operations : List.of(retrying.logical(), retrying.auth(),
                retrying.sys(), retrying.pki(), retrying.pki("root-ca"), retrying.database(),
                retrying.database("db123"), retrying.leases(), retrying.debug(),
                retrying.mounts(), retrying.seal())) {
            assertEquals(operations.getClass().getName(), policy, retryPolicyOf(operations));
        }
        for (final OperationsBase operations : List.of(vault.logical(), vault.pki(),
                vault.database())) {
            assertNull(operations.getClass().getName(), retryPolicyOf(operations));
        }
    }

    @Test
    public void testRetrying_PkiAndDatabaseUseDefaultMountPaths() throws Exception {
        final var retrying = Vault.create(mockConfig()).retrying(5, 10);
        assertEquals("pki", fieldOf(retrying.pki(), "mountPath"));
        assertEquals("database", fieldOf(retrying.database(), "mountPath"));
    }

    @Test
    public void testRetrying_RejectsNegativeValues() throws Exception {
        final var vault = Vault.create(mockConfig());
        assertThrows(IllegalArgumentException.class, () -> vault.retrying(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> vault.retrying(5, -1));
    }

    @Test
    public void testConfigRetries_SetsValues() throws Exception {
        final var config = new VaultConfig();
        assertSame(config, config.retries(5, 1000));
        assertEquals(5, config.getMaxRetries());
        assertEquals(1000, config.getRetryIntervalMilliseconds());
    }

    @Test
    public void testConfigRetries_AppliesToEveryCall() throws Exception {
        final var vault = Vault.create(new VaultConfig().address("http://127.0.0.1:8999")
                .token("mock_token").engineVersion(1).retries(2, 10).build());

        final var readServer = VaultTestUtils.initHttpMockVault(
                new RetriesMockVault(2, 200, READ_RESPONSE));
        readServer.start();
        try {
            assertEquals(2, vault.logical().read("secret/hello").getRetries());
        } finally {
            VaultTestUtils.shutdownMockVault(readServer);
        }

        final var revokeServer = VaultTestUtils.initHttpMockVault(
                new RetriesMockVault(2, 204, null));
        revokeServer.start();
        try {
            assertEquals(2, vault.sys().leases().revoke("lease-id").getRetries());
        } finally {
            VaultTestUtils.shutdownMockVault(revokeServer);
        }
    }

    @Test
    public void testConfigRetries_RejectsNegativeValues() throws Exception {
        final var config = new VaultConfig().retries(5, 1000);
        assertThrows(IllegalArgumentException.class, () -> config.retries(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> config.retries(3, -1));
        assertEquals(5, config.getMaxRetries());
        assertEquals(1000, config.getRetryIntervalMilliseconds());
    }

    @Test
    public void testConfigRetries_OverriddenByRetrying() throws Exception {
        final var server = VaultTestUtils.initHttpMockVault(
                new RetriesMockVault(3, 200, READ_RESPONSE));
        server.start();
        try {
            final var vault = Vault.create(mockConfig().retries(5, 10));
            final var e = assertThrows(VaultException.class,
                    () -> vault.retrying(1, 10).logical().read("secret/hello"));
            assertEquals(500, e.getHttpStatusCode());
        } finally {
            VaultTestUtils.shutdownMockVault(server);
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testWithRetries_Unchanged() throws Exception {
        final var config = mockConfig();
        final var vault = Vault.create(config);
        final var other = Vault.create(config);
        final var retrying = vault.retrying(1, 10);

        assertSame(vault, vault.withRetries(5, 1000));
        assertEquals(5, config.getMaxRetries());
        assertEquals(1000, config.getRetryIntervalMilliseconds());
        // Shared by every Vault using the config, but ignored by one returned by retrying()
        assertNull(retryPolicyOf(other.logical()));
        assertEquals(new RetryPolicy(1, 10), retryPolicyOf(retrying.logical()));

        // Negative values are still written to the config, not validated
        vault.withRetries(-1, -1);
        assertEquals(-1, config.getMaxRetries());
        assertEquals(-1, config.getRetryIntervalMilliseconds());
    }

    private static VaultConfig mockConfig() throws VaultException {
        return new VaultConfig().address("http://127.0.0.1:8999")
                .token("mock_token").engineVersion(1).build();
    }

    private static Object retryPolicyOf(final OperationsBase operations) throws Exception {
        return baseFieldOf(operations, "retryPolicy");
    }

    private static Object baseFieldOf(final OperationsBase operations, final String name)
            throws Exception {
        final var field = OperationsBase.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(operations);
    }

    private static Object fieldOf(final Object target, final String name) throws Exception {
        final var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

}
