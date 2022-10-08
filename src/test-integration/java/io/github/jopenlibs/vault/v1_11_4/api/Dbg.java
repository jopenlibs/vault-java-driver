package io.github.jopenlibs.vault.v1_11_4.api;

import io.github.jopenlibs.vault.v1_11_4.util.VaultContainer;
import java.io.IOException;
import org.junit.Test;

/**
 * <p>Integration tests for the debug-related operations on the Vault HTTP API's.</p>
 */
public class Dbg {
//    public static final VaultContainer container = new VaultContainer();

    @Test
    public void dbg() throws IOException, InterruptedException {
        VaultContainer container = new VaultContainer();
        container.start();
        container.initAndUnsealVault();

        container.stop();
    }
}
