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
import io.github.jopenlibs.vault.json.Json;
import io.github.jopenlibs.vault.json.JsonObject;
import io.github.jopenlibs.vault.json.JsonValue;
import io.github.jopenlibs.vault.rest.Rest;
import io.github.jopenlibs.vault.rest.RestException;
import io.github.jopenlibs.vault.rest.RestResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public interface Vault {

    static Vault create(VaultConfig vaultConfig){
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
    Mounts mounts();

    /**
     * @see Sys#seal()
     * @deprecated This method is deprecated and in future it will be removed
     */
    Seal seal();

    Map<String, String> getSecretEngineVersions();

}
