package io.github.jopenlibs.vault.vault.api;

import io.github.jopenlibs.vault.Vault;
import io.github.jopenlibs.vault.VaultConfig;
import io.github.jopenlibs.vault.json.JsonObject;
import io.github.jopenlibs.vault.json.JsonValue;
import io.github.jopenlibs.vault.response.UnwrapResponse;
import io.github.jopenlibs.vault.vault.VaultTestUtils;
import io.github.jopenlibs.vault.vault.mock.MockVault;
import org.eclipse.jetty.server.Server;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AuthUnwrapWithoutAuthResponseTest {
    private static final JsonObject RESPONSE_AUTH_UNWRAP = new JsonObject()
            .add("renewable", false)
            .add("lease_duration", 0)
            .add("data", new JsonObject()
                    .add("foo", "bar")
                    .add("zip", "zar"));

    private Server server;
    private MockVault vaultServer;

    @Before
    public void before() throws Exception {
        vaultServer = new MockVault(200, RESPONSE_AUTH_UNWRAP.toString());
        server = VaultTestUtils.initHttpMockVault(vaultServer);
        server.start();
    }

    @After
    public void after() throws Exception {
        VaultTestUtils.shutdownMockVault(server);
    }

    @Test
    public void unwrap_response_with_null_auth() throws Exception {
        VaultConfig vaultConfig = new VaultConfig().address("http://127.0.0.1:8999").token("wrappedToken").build();
        Vault vault = new Vault(vaultConfig);
        UnwrapResponse response = vault.auth().unwrap("wrappedToken");

        assertEquals(200, response.getRestResponse().getStatus());

        assertEquals("wrappedToken", vaultServer.getRequestHeaders().get("X-Vault-Token"));
        assertEquals("bar", response.getData().asObject().get("foo").asString());
        assertEquals("zar", response.getData().asObject().get("zip").asString());
    }
}
