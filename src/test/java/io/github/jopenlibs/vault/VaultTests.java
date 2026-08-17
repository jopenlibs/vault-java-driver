package io.github.jopenlibs.vault;

import io.github.jopenlibs.vault.mock.MockVault;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;


/**
 * Unit tests for the various <code>Vault</code> constructors.
 */
public class VaultTests {

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
            var vaultConfig = new VaultConfig().nameSpace("");
        } catch (VaultException e) {
            Assert.assertEquals(e.getMessage(), "A namespace cannot be empty.");
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
}
