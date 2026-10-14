package io.github.jopenlibs.vault;

import io.github.jopenlibs.vault.api.Auth;
import io.github.jopenlibs.vault.api.Debug;
import io.github.jopenlibs.vault.api.Logical;
import io.github.jopenlibs.vault.api.database.Database;
import io.github.jopenlibs.vault.api.pki.Pki;
import io.github.jopenlibs.vault.api.sys.Leases;
import io.github.jopenlibs.vault.api.sys.Seal;
import io.github.jopenlibs.vault.api.sys.Sys;
import io.github.jopenlibs.vault.api.sys.mounts.Mounts;
import java.util.Map;

public interface Vault {

    static Vault create(VaultConfig vaultConfig) {
        return new VaultImpl(vaultConfig);
    }

    /**
     * Construct a Vault driver instance with the provided config settings, and use the provided
     * global KV Engine version for all secrets.
     *
     * @param vaultConfig Configuration settings for Vault interaction (e.g. server address, token,
     * etc)
     * @param engineVersion Which version of the Key/Value Secret Engine to use globally (i.e. 1 or
     * 2)
     */
    static Vault create(final VaultConfig vaultConfig, final Integer engineVersion) {
        return new VaultImpl(vaultConfig, engineVersion);
    }

    /**
     * Construct a Vault driver instance with the provided config settings.
     *
     * @param vaultConfig Configuration settings for Vault interaction (e.g. server address, token,
     * etc) If the Secrets engine version path map is not provided, or does not contain the
     * requested secret, fall back to the global version supplied.
     * @param useSecretsEnginePathMap Whether to use a provided KV Engine version map from the Vault
     * config, or generate one. If a secrets KV Engine version map is not supplied, use Vault APIs
     * to determine the KV Engine version for each secret. This call requires admin rights.
     * @param globalFallbackVersion The Integer version of the KV Engine to use as a global
     * fallback.
     * @throws VaultException If any error occurs
     */
    static Vault create(final VaultConfig vaultConfig, final Boolean useSecretsEnginePathMap,
            final Integer globalFallbackVersion)
            throws VaultException {
        return new VaultImpl(vaultConfig, useSecretsEnginePathMap, globalFallbackVersion);
    }

    /**
     * This method is chained ahead of endpoints (e.g. <code>logical()</code>, <code>auth()</code>,
     * etc... to specify retry rules for any API operations invoked on that endpoint.
     *
     * @param maxRetries The number of times that API operations will be retried when a failure
     * occurs
     * @param retryIntervalMilliseconds The number of milliseconds that the driver will wait in
     * between retries
     * @return This object, with maxRetries and retryIntervalMilliseconds populated
     */
    Vault withRetries(final int maxRetries, final int retryIntervalMilliseconds);

    /**
     * <p>Returns a new <code>Vault</code> whose API operations are retried with the given rules,
     * overriding the retries set in the configuration. This instance, and its configuration, are
     * left unchanged:</p>
     *
     * <blockquote>
     * <pre>{@code
     * final LogicalResponse response = vault.retrying(5, 1000).logical().read("secret/hello");
     * }</pre>
     * </blockquote>
     *
     * <p>The returned instance shares the configuration of this one, so later changes to it (e.g.
     * a new token) apply to both.</p>
     *
     * <p>The default implementation throws <code>UnsupportedOperationException</code>, so that
     * existing implementations of this interface keep compiling.</p>
     *
     * @param maxRetries The number of times that API operations will be retried when a failure
     * occurs
     * @param retryIntervalMilliseconds The number of milliseconds that the driver will wait in
     * between retries
     * @return A new <code>Vault</code> using the given retries
     * @throws IllegalArgumentException If either value is negative
     * @throws UnsupportedOperationException If this implementation does not support it
     * @see RetryPolicy
     */
    default Vault retrying(final int maxRetries, final int retryIntervalMilliseconds) {
        throw new UnsupportedOperationException(
                getClass().getName() + " does not support retrying()");
    }

    /**
     * Returns the implementing class for Vault's core/logical operations (e.g. read, write).
     *
     * @return The implementing class for Vault's core/logical operations (e.g. read, write)
     */
    Logical logical();

    /**
     * Returns the implementing class for operations on Vault's <code>/v1/auth/*</code> REST
     * endpoints
     *
     * @return The implementing class for Vault's auth operations.
     */
    Auth auth();

    /**
     * Returns the implementing class for operations on Vault's <code>/v1/sys/*</code> REST
     * endpoints
     *
     * @return The implementing class for Vault's auth operations.
     */
    Sys sys();

    /**
     * Returns the implementing class for Vault's PKI secret backend (i.e. <code>/v1/pki/*</code>
     * REST endpoints).
     *
     * @return The implementing class for Vault's PKI secret backend.
     */
    Pki pki();

    /**
     * <p>Returns the implementing class for Vault's PKI secret backend, using a custom path when
     * that backend is mounted on something other than the default (i.e. <code>/v1/pki</code>).</p>
     *
     * <p>For instance, if your PKI backend is instead mounted on <code>/v1/root-ca</code>, then
     * <code>"root-ca"</code>
     * would be passed via the <code>mountPath</code> parameter.  Example usage:</p>
     *
     * <blockquote>
     * <pre>{@code
     * final VaultConfig config = new VaultConfig().address(...).token(...).build();
     * final Vault vault = Vault.create(config);
     * final PkiResponse response = vault.pki("root-ca").createOrUpdateRole("testRole");
     *
     * assertEquals(204, response.getRestResponse().getStatus());
     * }</pre>
     * </blockquote>
     *
     * @param mountPath The path on which your Vault PKI backend is mounted, without the
     * <code>/v1/</code> prefix
     * @return The implementing class for Vault's PKI secret backend.
     */
    Pki pki(final String mountPath);

    Database database();

    Database database(final String mountPath);

    /**
     * @see Sys#leases()
     * @deprecated This method is deprecated and in future it will be removed
     */
    @Deprecated
    Leases leases();

    /**
     * Returns the implementing class for Vault's debug operations (e.g. raw, health).
     *
     * @return The implementing class for Vault's debug operations (e.g. raw, health)
     */
    Debug debug();

    /**
     * @see Sys#mounts()
     * @deprecated This method is deprecated and in future it will be removed
     */
    @Deprecated
    Mounts mounts();

    /**
     * @see Sys#seal()
     * @deprecated This method is deprecated and in future it will be removed
     */
    @Deprecated
    Seal seal();

    Map<String, String> getSecretEngineVersions();

}
