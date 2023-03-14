package io.github.jopenlibs.vault.api;

import io.github.jopenlibs.vault.VaultConfig;
import io.github.jopenlibs.vault.VaultException;
import io.github.jopenlibs.vault.response.SealResponse;

/**
 * @see io.github.jopenlibs.vault.api.sys.Seal
 * @deprecated This class is deprecated and in future it will be removed
 */
public class Seal {

    private final io.github.jopenlibs.vault.api.sys.Seal seal;

    public Seal(final VaultConfig config) {
        this.seal = new io.github.jopenlibs.vault.api.sys.Seal(config);
    }

    public io.github.jopenlibs.vault.api.sys.Seal withNameSpace(final String nameSpace) {
        return this.seal.withNameSpace(nameSpace);
    }

    public SealResponse seal() throws VaultException {
        return this.seal.seal();
    }

    public SealResponse unseal(final String key) throws VaultException {
        return this.seal.unseal(key, false);
    }

    public SealResponse unseal(final String key, final Boolean reset) throws VaultException {
        return this.seal.unseal(key, reset);
    }

    public SealResponse sealStatus() throws VaultException {
        return this.seal.sealStatus();
    }
}
