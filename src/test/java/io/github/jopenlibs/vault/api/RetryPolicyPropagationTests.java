package io.github.jopenlibs.vault.api;

import io.github.jopenlibs.vault.RetryPolicy;
import io.github.jopenlibs.vault.VaultConfig;
import io.github.jopenlibs.vault.VaultException;
import io.github.jopenlibs.vault.VaultTestUtils;
import io.github.jopenlibs.vault.api.database.Database;
import io.github.jopenlibs.vault.api.pki.Pki;
import io.github.jopenlibs.vault.api.sys.Leases;
import io.github.jopenlibs.vault.api.sys.Seal;
import io.github.jopenlibs.vault.api.sys.Sys;
import io.github.jopenlibs.vault.api.sys.Wrapping;
import io.github.jopenlibs.vault.api.sys.mounts.Mounts;
import io.github.jopenlibs.vault.mock.RetriesMockVault;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * Tests that the operation classes keep the {@link RetryPolicy} they are given, and pass it to the
 * operation classes they create.
 */
public class RetryPolicyPropagationTests {

    private static final RetryPolicy POLICY = new RetryPolicy(3, 10);

    @Test
    public void testConstructorsKeepPolicy() throws VaultException {
        final var config = config();
        assertSame(POLICY, new Logical(config, POLICY).retryPolicy);
        assertSame(POLICY, new Auth(config, POLICY).retryPolicy);
        assertSame(POLICY, new Debug(config, POLICY).retryPolicy);
        assertSame(POLICY, new Sys(config, POLICY).retryPolicy);
        assertSame(POLICY, new Leases(config, POLICY).retryPolicy);
        assertSame(POLICY, new Seal(config, POLICY).retryPolicy);
        assertSame(POLICY, new Wrapping(config, POLICY).retryPolicy);
        assertSame(POLICY, new Mounts(config, POLICY).retryPolicy);
        assertSame(POLICY, new Pki(config, "pki", POLICY).retryPolicy);
        assertSame(POLICY, new Database(config, "database", POLICY).retryPolicy);
    }

    @Test
    public void testConfigOnlyConstructorsHaveNoPolicy() throws VaultException {
        final var config = config();
        assertNull(new Logical(config).retryPolicy);
        assertNull(new Auth(config).retryPolicy);
        assertNull(new Debug(config).retryPolicy);
        assertNull(new Sys(config).retryPolicy);
        assertNull(new Leases(config).retryPolicy);
        assertNull(new Seal(config).retryPolicy);
        assertNull(new Wrapping(config).retryPolicy);
        assertNull(new Mounts(config).retryPolicy);
        assertNull(new Pki(config).retryPolicy);
        assertNull(new Pki(config, "root-ca").retryPolicy);
        assertNull(new Database(config).retryPolicy);
        assertNull(new Database(config, "db123").retryPolicy);
    }

    @Test
    public void testPkiAndDatabaseKeepMountPathConstructorWithNull() throws VaultException {
        final var config = config();
        // Must still compile: a (VaultConfig, RetryPolicy) overload would make these ambiguous
        assertNull(new Pki(config, null).retryPolicy);
        assertNull(new Database(config, null).retryPolicy);
    }

    @Test
    public void testSysPassesPolicy() throws VaultException {
        final var sys = new Sys(config(), POLICY);
        assertSame(POLICY, sys.wrapping().retryPolicy);
        assertSame(POLICY, sys.seal().retryPolicy);
        assertSame(POLICY, sys.mounts().retryPolicy);
        assertSame(POLICY, sys.leases().retryPolicy);
    }

    @Test
    public void testSysWithoutPolicyPassesNull() throws VaultException {
        final var sys = new Sys(config());
        assertNull(sys.wrapping().retryPolicy);
        assertNull(sys.seal().retryPolicy);
        assertNull(sys.mounts().retryPolicy);
        assertNull(sys.leases().retryPolicy);
    }

    @Test
    public void testAuthPassesPolicyToSys() throws Exception {
        // The config allows no retries, so the call only succeeds if Auth passes its policy on
        final var server = VaultTestUtils.initHttpMockVault(
                new RetriesMockVault(2, 200, "{\"data\":{\"token\":\"mock\"}}"));
        server.start();
        try {
            final var response = new Auth(config(), POLICY).lookupWrap();
            assertEquals(2, response.getRetries());
        } finally {
            VaultTestUtils.shutdownMockVault(server);
        }
    }

    private static VaultConfig config() throws VaultException {
        return new VaultConfig().address("http://127.0.0.1:8999").token("mock_token")
                .engineVersion(1).build();
    }
}
