package io.github.jopenlibs.vault.api;

import io.github.jopenlibs.vault.VaultException;
import io.github.jopenlibs.vault.response.SealResponse;
import io.github.jopenlibs.vault.util.VaultContainer;
import java.io.IOException;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Integration tests for the seal-related (i.e. "seal") Vault API operations.
 */
public class SealTests {

    @ClassRule
    public static final VaultContainer container = new VaultContainer();

    private static String unsealKey = null;

    @BeforeClass
    public static void setupClass() throws IOException, InterruptedException {
        container.initAndUnsealVault();
        unsealKey = container.getUnsealKey();
    }

    @Test
    public void testSealStatus_returnsFalse_fromInitialUnsealedState() throws VaultException {
        // Due to the "setupClass()" static method, the Vault instance should be in an unsealed state
        // at the start of this test suite.
        final SealResponse response = container.getVault().sys().seal().sealStatus();

        assertFalse(response.getSealed());
        assertEquals(1, response.getNumberOfShares().longValue());
        assertEquals(1, response.getThreshold().longValue());
        assertEquals(0, response.getProgress().longValue());
    }

    @Test
    public void testSealAndUnseal_togglesAndRestoresUnsealedState() throws VaultException {
        // Seal Vault and verify its status
        container.getRootVault().sys().seal().seal();
        final SealResponse sealResponse = container.getRootVault().sys().seal().sealStatus();
        assertTrue(sealResponse.getSealed());

        // Unseal Vault again, and verify its status
        container.getRootVault().sys().seal().unseal(unsealKey);
        final SealResponse unsealResponse = container.getRootVault().sys().seal().sealStatus();
        assertFalse(unsealResponse.getSealed());
    }

}
