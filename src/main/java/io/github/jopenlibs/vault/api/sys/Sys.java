package io.github.jopenlibs.vault.api.sys;

import io.github.jopenlibs.vault.Vault;
import io.github.jopenlibs.vault.VaultConfig;
import io.github.jopenlibs.vault.api.OperationsBase;

/**
 * <p>The implementing class for operations on Vault's <code>/v1/sys/*</code> REST endpoints.</p>
 *
 * <p>This class is not intended to be constructed directly.  Rather, it is meant to used by way of
 * <code>Vault</code> in a DSL-style builder pattern.  See the Javadoc comments of each
 * <code>public</code> method for usage examples.</p>
 *
 * @see Vault#sys()
 */
public class Sys extends OperationsBase {

    private String nameSpace;

    public Sys(final VaultConfig config) {
        super(config);

        if (this.config.getNameSpace() != null && !this.config.getNameSpace().isEmpty()) {
            this.nameSpace = this.config.getNameSpace();
        }
    }

    /**
     * Returns the implementing class for <code>/v1/sys/wrapping/*</code> REST endpoints
     *
     * @return The implementing class for wrapping operations
     */
    public Wrapping wrapping() {
        return new Wrapping(this.config);
    }
}
