package io.github.jopenlibs.vault;

import io.github.jopenlibs.vault.mock.MockVault;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;


/**
 * Unit tests for the various <code>Vault</code> constructors.
 */
public class VaultTests {

    @Test(expected = IllegalArgumentException.class)
    public void testNullEngineVersionIsRejected() throws VaultException {
        final var config = new VaultConfig().address("http://127.0.0.1:8200").build();
        Vault.create(config, (Integer) null);
    }

    @Test
    public void testNullUseSecretsEnginePathMapIsFalse() throws VaultException {
        final var config = new VaultConfig().address("http://127.0.0.1:8200").build();
        final var vault = Vault.create(config, null, 1);
        Assert.assertEquals(Integer.valueOf(1),
                vault.logical().getEngineVersionForSecretPath("secret/hello"));
    }

    @Test
    public void testUnsetEngineVersionDefaultsTo2() throws VaultException {
        final var config = new VaultConfig().address("http://127.0.0.1:8200").build();
        final var vault = Vault.create(config, false, null);
        Assert.assertEquals(Integer.valueOf(2),
                vault.logical().getEngineVersionForSecretPath("secret/hello"));
    }

    @Test
    public void testDefaultVaultConstructor() {
        var vaultConfig = new VaultConfig();
        var vault = Vault.create(vaultConfig);
        Assert.assertNotNull(vault);
        Assert.assertEquals(String.valueOf(2),
                vault.logical().getEngineVersionForSecretPath("*").toString());
    }

    @Test
    public void testGlobalEngineVersionVaultConstructor() {
        var vaultConfig = new VaultConfig();
        var vault = Vault.create(vaultConfig, 1);
        Assert.assertNotNull(vault);
        Assert.assertEquals(String.valueOf(1),
                vault.logical().getEngineVersionForSecretPath("*").toString());
    }

    @Test
    public void testNameSpaceProvidedVaultConstructor() throws VaultException {
        var vaultConfig = new VaultConfig().nameSpace("testNameSpace");
        var vault = Vault.create(vaultConfig, 1);
        Assert.assertNotNull(vault);
    }

    @Test
    public void testNameSpaceProvidedVaultConstructorCannotBeEmpty() {
        try {
            new VaultConfig().nameSpace("").address("address").build();
            Assert.fail("Expected a VaultException to be thrown");
        } catch (VaultException e) {
            Assert.assertEquals("A namespace cannot be empty.", e.getMessage());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidGlobalEngineVersionVaultConstructor() {
        var vaultConfig = new VaultConfig();
        var vault = Vault.create(vaultConfig, 3);
        Assert.assertNull(vault);
    }

    @Test(expected = VaultException.class)
    public void testVaultWithNoKVEnginePathMap() throws VaultException {
        var vaultConfig = new VaultConfig();
        var vault = Vault.create(vaultConfig, true, 1);
        Assert.assertNull(vault);
    }

    @Test(expected = VaultException.class)
    public void testVaultWithEmptyKVEnginePathMap() throws VaultException {
        Map<String, String> emptyEngineKVMap = Map.of();
        var vaultConfig = new VaultConfig().secretsEnginePathMap(emptyEngineKVMap);
        var vault = Vault.create(vaultConfig, true, 1);
        Assert.assertNull(vault);
    }

    @Test
    public void testVaultWithUnknownKVEnginePathMap() throws VaultException {
        Map<String, String> engineKVMap = Map.of("secret/", "unknown");
        var vaultConfig = new VaultConfig().secretsEnginePathMap(engineKVMap);
        var vault = Vault.create(vaultConfig, true, 1);
        Assert.assertNotNull(vault);
        Assert.assertEquals(String.valueOf(1),
                vault.logical().getEngineVersionForSecretPath("secret").toString());
    }

    @Test
    public void testVaultWithoutKVEnginePathMap() throws VaultException {
        Map<String, String> engineKVMap = Map.of("/hello", "2");
        var vaultConfig = new VaultConfig().secretsEnginePathMap(engineKVMap);
        var vault = Vault.create(vaultConfig, false, 1);
        Assert.assertNotNull(vault);
        Assert.assertEquals(String.valueOf(1),
                vault.logical().getEngineVersionForSecretPath("/hello").toString());
        Assert.assertEquals(String.valueOf(1),
                vault.logical().getEngineVersionForSecretPath("notInMap").toString());
    }

    @Test
    public void kvEngineMapIsHonored() throws VaultException {
        Map<String, String> testMap = Map.of("kv-v1/", "1");
        var vaultConfig = new VaultConfig().secretsEnginePathMap(testMap);
        Assert.assertNotNull(vaultConfig);
        var vault = Vault.create(vaultConfig, true, 2);
        Assert.assertNotNull(vault);
        Assert.assertEquals(String.valueOf(1),
                vault.logical().getEngineVersionForSecretPath("kv-v1").toString());
        Assert.assertEquals(String.valueOf(2),
                vault.logical().getEngineVersionForSecretPath("notInMap").toString());
    }

    @Test
    public void testVaultWithPrefixedKVEnginePathMap() throws VaultException {
        Map<String, String> engineKVMap = Map.of("secret/", "2", "other/mount/", "2");
        var vaultConfig = new VaultConfig().secretsEnginePathMap(engineKVMap);
        var vault = Vault.create(vaultConfig, true, 1);
        Assert.assertNotNull(vault);
        Assert.assertEquals(String.valueOf(2),
                vault.logical().getEngineVersionForSecretPath("secret/path/to/credential").toString());
        Assert.assertEquals(String.valueOf(2),
                vault.logical().getEngineVersionForSecretPath("other/mount/path/to/credential").toString());
        Assert.assertEquals(String.valueOf(1),
                vault.logical().getEngineVersionForSecretPath("other").toString());
        Assert.assertEquals(String.valueOf(1),
                vault.logical().getEngineVersionForSecretPath("notInMap").toString());
    }

    @Test
    public void testConfigBuiler_WithInvalidRequestAsNonError() throws Exception {
        final var mockVault = new MockVault(403,
                "{\"errors\":[\"preflight capability check returned 403, please ensure client's policies grant access to path \"path/that/does/not/exist/\"]}");
        final var server = VaultTestUtils.initHttpMockVault(mockVault);
        server.start();

        final var vaultConfig = new VaultConfig()
                .address("http://127.0.0.1:8999")
                .token("mock_token")
                .build();
        final var vault = Vault.create(vaultConfig);

        var response = vault.logical().read("path/that/does/not/exist/");
        VaultTestUtils.shutdownMockVault(server);
        Assert.assertEquals(403, response.getRestResponse().getStatus());
        Assert.assertEquals(0, response.getRetries());
    }

    /**
     * When the KV Engine version map cannot be generated (here, Vault denies access to
     * <code>sys/mounts</code>), a <code>VaultException</code> with a clear message is thrown.
     */
    @Test
    public void testVaultWithKVEnginePathMapAccessDenied() throws Exception {
        final var server = VaultTestUtils.initHttpMockVault(new MockVault(403, "{}"));
        server.start();
        try {
            final var vaultConfig = new VaultConfig()
                    .address("http://127.0.0.1:8999")
                    .token("mock_token")
                    .build();
            Vault.create(vaultConfig, true, 1);
            Assert.fail("Expected a VaultException to be thrown");
        } catch (VaultException e) {
            Assert.assertEquals(
                    "An Engine KV version map was not supplied, and unable to determine KV "
                            + "Engine version. Do you have admin rights?", e.getMessage());
        } finally {
            VaultTestUtils.shutdownMockVault(server);
        }
    }
}
