package io.github.jopenlibs.vault;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

/**
 * Unit tests for the <code>VaultConfig</code> settings loader.
 */
public class VaultConfigTests {

    /**
     * <p>The code used by <code>VaultConfig</code> to load environment variables is encapsulated
     * within an inner class, so that a mock version of that environment loader can be used by unit
     * tests.</p>
     *
     * <p>This mock implementation of <code>VaultConfig.EnvironmentLoader</code> allows unit tests
     * to declare values that should be returned for a given environment variable name.  The actual
     * environment is never used.</p>
     *
     * <p>The <code>VAULT_TOKEN</code> variable gets special treatment.  If a value cannot be found
     * in the environment, then {@link EnvironmentLoader} looks for a <code>.vault-token</code> file
     * in the user's home directory.  So this mock has a second constructor which allows you to pass
     * a directory path, to serve as a mock "home directory" for testing.</p>
     */
    class MockEnvironmentLoader extends EnvironmentLoader {

        final Map<String, String> overrides;
        final String mockHomeDirectory;

        public MockEnvironmentLoader() {
            overrides = new HashMap<>();
            mockHomeDirectory = "";
        }

        private MockEnvironmentLoader(final String mockHomeDirectory) {
            overrides = new HashMap<>();
            this.mockHomeDirectory = mockHomeDirectory;
        }

        /**
         * Declare a variable and value to be available in the mock "environment".  This method may
         * be called repeatedly, to populate multiple variables.  This method should be called prior
         * to passing the object instance to a <code>VaultConfig</code> constructor, or calling the
         * <code>build()</code> method on that class.
         *
         * @param name Mock environment variable name
         * @param value Mock environment variable value
         */
        private void override(final String name, final String value) {
            this.overrides.put(name, value);
        }

        @Override
        public String loadVariable(final String name) {
            String value = null;
            if ("VAULT_TOKEN".equals(name)) {
                if (overrides.containsKey("VAULT_TOKEN")) {
                    value = overrides.get("VAULT_TOKEN");
                } else {
                    try {
                        final byte[] bytes = Files.readAllBytes(
                                Paths.get(mockHomeDirectory).resolve(".vault-token"));
                        value = new String(bytes, StandardCharsets.UTF_8).trim();
                    } catch (IOException ignored) {
                    }
                }
            } else {
                value = overrides.get(name);
            }
            return value;
        }

    }

    /**
     * Test creating a new <code>VaultConfig</code> via its constructor, passing address and token
     * values and ensuring that they're later accessible.
     */
    @Test
    public void testConfigConstructor() throws VaultException {
        final var config = new VaultConfig().address("address").token("token").build();
        assertEquals("address", config.getAddress());
        assertEquals("token", new String(config.getToken()));
    }

    /**
     * Test creating a new <code>VaultConfig</code> via its constructor, ensuring that addresses are
     * normalized to not have a trailing slash.
     */
    @Test
    public void testConfigConstructor_NormalizesAddress() throws VaultException {
        final var config = new VaultConfig().address("https://localhost:8200/").build();
        assertEquals("https://localhost:8200", config.getAddress());
    }

    /**
     * Test creating a new <code>VaultConfig</code> via its constructor, passing null address and
     * token values AND having them unavailable in the environment variables too.  This should cause
     * initialization failure.
     */
    @Test(expected = VaultException.class)
    public void testConfigConstructor_FailToLoad() throws VaultException {
        new VaultConfig().build();
    }

    /**
     * Test creating a <code>VaultConfig</code> instance via its builder pattern, explicitly
     * specifying address and token values.
     */
    @Test
    public void testConfigBuilder() throws VaultException {
        Map<String, String> testMap = Map.of("foo", "bar");
        final VaultConfig config =
                new VaultConfig()
                        .address("address")
                        .token("token")
                        .engineVersion(1)
                        .secretsEnginePathMap(testMap)
                        .build();
        assertEquals("address", config.getAddress());
        assertEquals("token", new String(config.getToken()));
        assertEquals("1", config.getGlobalEngineVersion().toString());
        assertEquals("bar", config.getSecretsEnginePathMap().get("foo"));
    }

    /**
     * Test creating a <code>VaultConfig</code> instance via its builder pattern, forcing it to look
     * to the environment variables for address and token values.
     */
    @Test
    public void testConfigBuilder_LoadFromEnv() throws VaultException {
        final var mock = new MockEnvironmentLoader();
        mock.override("VAULT_ADDR", "http://127.0.0.1:8200");
        mock.override("VAULT_TOKEN", "c24e2469-298a-6c64-6a71-5b47c9ba459a");
        mock.override("VAULT_PROXY_ADDRESS", "localhost");
        mock.override("VAULT_PROXY_PORT", "80");
        mock.override("VAULT_PROXY_USERNAME", "scott");
        mock.override("VAULT_PROXY_PASSWORD", "tiger");
        mock.override("VAULT_SSL_VERIFY", "true");
        mock.override("VAULT_OPEN_TIMEOUT", "30");
        mock.override("VAULT_READ_TIMEOUT", "30");

        final var config = new VaultConfig()
                .environmentLoader(mock)
                .build();
        assertEquals("http://127.0.0.1:8200", config.getAddress());
        assertEquals("c24e2469-298a-6c64-6a71-5b47c9ba459a", new String(config.getToken()));
        assertTrue(config.getSslConfig().isVerify());
        assertTrue(30 == config.getOpenTimeout());
        assertTrue(30 == config.getReadTimeout());
    }

    @Test
    public void testConfigBuilder_LoadFromEnv_SslCert() throws IOException, VaultException {
        final String tempDirectoryPath = System.getProperty("java.io.tmpdir");
        final String pemPath = tempDirectoryPath + File.separator + "cert.pem";
        try (
                final InputStream input = this.getClass().getResourceAsStream("/cert.pem");
                final var output = new FileOutputStream(pemPath)
        ) {
            int nextChar;
            while ((nextChar = input.read()) != -1) {
                output.write((char) nextChar);
            }
        }

        final var mock = new MockEnvironmentLoader();
        mock.override("VAULT_ADDR", "http://127.0.0.1:8200");
        mock.override("VAULT_SSL_CERT", pemPath);
        final var config = new VaultConfig()
                .environmentLoader(mock)
                .build();

        final String expected = "-----BEGIN CERTIFICATE-----MIIDhjCCAm6gAwIBAgIES40FSTANBgkqhkiG9w0BAQsFADBrMQswCQYDVQQGEwJVUzERMA8GA1UECBMIQW55c3RhdGUxEDAOBgNVBAcTB0FueXRvd24xETAPBgNVBAoTCFRlc3QgT3JnMRAwDgYDVQQLEwdUZXN0IE9VMRIwEAYDVQQDEwlUZXN0IFVzZXIwHhcNMTYwMjE2MTcwNDQ3WhcNMTYwNTE2MTcwNDQ3WjBrMQswCQYDVQQGEwJVUzERMA8GA1UECBMIQW55c3RhdGUxEDAOBgNVBAcTB0FueXRvd24xETAPBgNVBAoTCFRlc3QgT3JnMRAwDgYDVQQLEwdUZXN0IE9VMRIwEAYDVQQDEwlUZXN0IFVzZXIwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCHNAd93WjoDl7EYddxqpAd9FGoyvFA0900tmLJWmD3YPXhkOkXO38E//tS9KkXD39tDsDwHxw53iF1SmzgrHvzJzQvGjR5rvp7KjMhv/wlpED2E4FR/q2WigoXVtzpOwc4fk4PizBZV4fkSOtiQA0LEoQochw8wp7OI1tzE5iISKggD0N9EOJUzwQIcAgkAdaYEP9Fd2YMgTJAiHSakOgQowKQQGmIbKg0YWici9tiojwNCuNlcp1kBEUi4odO6BxRs8RKk6McvHCu1+2SSlxctGGU8kFKsF92/sULxvHAOovYspKdBJfw2f088Hnfw3jSgaWWQNB+oilVsfECx1BPAgMBAAGjMjAwMA8GA1UdEQQIMAaHBH8AAAEwHQYDVR0OBBYEFHuppZEESxlasbK5aq4LvF/IhtseMA0GCSqGSIb3DQEBCwUAA4IBAQBK9g8sWk6jCPekk2VjK6aKYIs4BB79xsaj41hdjoMwVRSelSpsmJE34/Vflqy+SBrf59czvk/UqJIYSrHRx7M0hpfIChmqqNEj5NKY+MFBuOTt4r/Wv3tbBTf2CMs4hLnkevhleNLxJhAjvh7r52U+uE8l6O11dsQRVXOSGnwdnvInVTs1ilxdTQh680DEU0q26P3o36N3Oxxgls2ZC3ExnLJnOofhj01l6cYhI06RtFPzJtv5sICCkYGMDKSIsWUndmurZjLAjsAKPT/RePeqyW0dKY5ZjtC+YAg5i3O0DLhERsDZECIp56oqsYxATuoHVzbjorM2ua2pUcuIR0p3-----END CERTIFICATE-----";
        final String actual = config.getSslConfig().getPemUTF8()
                .replaceAll(System.lineSeparator(), "");
        assertEquals(actual, expected);
    }

    @Test(expected = VaultException.class)
    public void testConfigBuilder_LoadFromEnv_SslCert_NotFound() throws VaultException {
        final var mock = new MockEnvironmentLoader();
        mock.override("VAULT_ADDR", "http://127.0.0.1:8200");
        mock.override("VAULT_SSL_CERT", "doesnt-exist.pem");
        new VaultConfig()
                .environmentLoader(mock)
                .build();
    }

    /**
     * Test creating a <code>VaultConfig</code> instance via its builder pattern, with no address no
     * token values passed OR available in the environment.  This should cause initialization
     * failure.
     */
    @Test(expected = VaultException.class)
    public void testConfigBuilder_FailToLoad() throws VaultException {
        new VaultConfig().build();
    }

    @SuppressWarnings("CharsetObjectCanBeUsed") // Requires Java 10 and above
    @Test
    public void testConfigBuilder_LoadTokenFromHomedir() throws IOException, VaultException {
        final String mockHomeDirectory =
                System.getProperty("java.io.tmpdir") + File.separatorChar + UUID.randomUUID()
                        .toString();
        assertTrue(new File(mockHomeDirectory).mkdirs());
        final var mockTokenFile = new File(
                mockHomeDirectory + File.separatorChar + ".vault-token");
        assertTrue(mockTokenFile.createNewFile());
        try (final PrintWriter out = new PrintWriter(mockTokenFile, StandardCharsets.UTF_8)) {
            out.println("d24e2469-298a-6c64-6a71-5b47c9ba459a");
        }

        final var mock = new MockEnvironmentLoader(mockHomeDirectory);
        mock.override("VAULT_ADDR", "http://127.0.0.1:8200");
        mock.override("VAULT_PROXY_ADDRESS", "localhost");
        mock.override("VAULT_PROXY_PORT", "80");
        mock.override("VAULT_PROXY_USERNAME", "scott");
        mock.override("VAULT_PROXY_PASSWORD", "tiger");
        mock.override("VAULT_SSL_VERIFY", "true");
        mock.override("VAULT_OPEN_TIMEOUT", "30");
        mock.override("VAULT_READ_TIMEOUT", "30");

        final var config = new VaultConfig()
                .environmentLoader(mock)
                .build();
        assertEquals("http://127.0.0.1:8200", config.getAddress());
        assertEquals("d24e2469-298a-6c64-6a71-5b47c9ba459a", new String(config.getToken()));
        assertTrue(config.getSslConfig().isVerify());
        assertEquals(30, (int) config.getOpenTimeout());
        assertEquals(30, (int) config.getReadTimeout());

        assertTrue(mockTokenFile.delete());
        assertTrue(new File(mockHomeDirectory).delete());
    }

    @Test
    public void testConfigBuilder_WithNamespace() throws VaultException {
        var vaultConfig = new VaultConfig().nameSpace("namespace").address("address")
                .build();
        Assert.assertEquals(vaultConfig.getNameSpace(), "namespace");
    }

    private final List<LogRecord> sslConfigWarnings = new ArrayList<>();

    private final Handler sslConfigWarningsHandler = new Handler() {
        @Override
        public void publish(final LogRecord record) {
            if (record.getLevel() == Level.WARNING) {
                sslConfigWarnings.add(record);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    @Before
    public void captureSslConfigWarnings() {
        Logger.getLogger(SslConfig.class.getCanonicalName()).addHandler(sslConfigWarningsHandler);
    }

    @After
    public void releaseSslConfigWarnings() {
        Logger.getLogger(SslConfig.class.getCanonicalName())
                .removeHandler(sslConfigWarningsHandler);
    }

    private boolean sslVerifyFromEnv(final String value) throws VaultException {
        final var mock = new MockEnvironmentLoader();
        mock.override("VAULT_ADDR", "http://127.0.0.1:8200");
        mock.override("VAULT_SSL_VERIFY", value);
        return new VaultConfig().environmentLoader(mock).build().getSslConfig().isVerify();
    }

    /**
     * Only an explicit <code>false</code> may disable SSL verification.
     */
    @Test
    public void testConfigBuilder_LoadFromEnv_SslVerifyFalse() throws VaultException {
        for (final String value : new String[]{"false", "FALSE", " false\n"}) {
            Assert.assertFalse("\"" + value + "\"", sslVerifyFromEnv(value));
        }
        Assert.assertEquals(List.of(), sslConfigWarnings);
    }

    /**
     * The recognized true values, unset and empty values enable SSL verification without warnings.
     */
    @Test
    public void testConfigBuilder_LoadFromEnv_SslVerifyTrue() throws VaultException {
        for (final String value : new String[]{"t", "T", "true", "TRUE", " true", "1", "y", "Y", "yes", "YES", "on", "On\n", ""}) {
            Assert.assertTrue("\"" + value + "\"", sslVerifyFromEnv(value));
        }
        Assert.assertTrue(sslVerifyFromEnv(null));
        Assert.assertEquals(List.of(), sslConfigWarnings);
    }

    /**
     * Any other value, including typos and other "false-like" values, keeps SSL verification
     * enabled, and logs a warning.
     */
    @Test
    public void testConfigBuilder_LoadFromEnv_SslVerifyFailsClosed() throws VaultException {
        final String[] values = {"0", "f", "n", "no", "off", "ture", "disabled"};
        for (final String value : values) {
            Assert.assertTrue("\"" + value + "\"", sslVerifyFromEnv(value));
        }
        Assert.assertEquals(values.length, sslConfigWarnings.size());
    }

    /**
     * An explicit <code>verify()</code> setting takes priority over the environment variable.
     */
    @Test
    public void testConfigBuilder_SslVerifyExplicitOverridesEnv() throws VaultException {
        final var mock = new MockEnvironmentLoader();
        mock.override("VAULT_SSL_VERIFY", "false");
        Assert.assertTrue(new SslConfig().environmentLoader(mock).verify(true).build().isVerify());

        mock.override("VAULT_SSL_VERIFY", "true");
        Assert.assertFalse(new SslConfig().environmentLoader(mock).verify(false).build().isVerify());
    }

    /**
     * Empty or blank values from any environment loader, even a custom one, are treated as unset.
     */
    @Test
    public void testConfigBuilder_LoadFromEnv_EmptyAddressIsUnset() {
        final var mock = new MockEnvironmentLoader();
        mock.override("VAULT_ADDR", "");
        try {
            new VaultConfig().environmentLoader(mock).build();
            Assert.fail("Expected VaultException");
        } catch (VaultException e) {
            assertEquals("No address is set", e.getMessage());
        }
    }

    @Test
    public void testConfigBuilder_LoadFromEnv_EmptyValuesAreUnset() throws VaultException {
        final var mock = new MockEnvironmentLoader();
        mock.override("VAULT_ADDR", "https://127.0.0.1:8200");
        mock.override("VAULT_TOKEN", "");
        mock.override("VAULT_OPEN_TIMEOUT", "");
        mock.override("VAULT_READ_TIMEOUT", " ");
        mock.override("VAULT_SSL_CERT", "");

        final var config = new VaultConfig().environmentLoader(mock).build();
        Assert.assertNull(config.getToken());
        Assert.assertNull(config.getOpenTimeout());
        Assert.assertNull(config.getReadTimeout());
        Assert.assertNull(config.getSslConfig().getSslContext());
    }

    /**
     * Surrounding whitespace (e.g. a trailing newline from a mounted secret) is removed from
     * values of any environment loader, even a custom one.
     */
    @Test
    public void testConfigBuilder_LoadFromEnv_ValuesAreTrimmed() throws IOException, VaultException {
        final var pemFile = Files.createTempFile("cert", ".pem");
        try (InputStream input = this.getClass().getResourceAsStream("/cert.pem")) {
            Files.write(pemFile, input.readAllBytes());
        }
        try {
            final var mock = new MockEnvironmentLoader();
            mock.override("VAULT_ADDR", " https://127.0.0.1:8200\n");
            mock.override("VAULT_TOKEN", "s.token\n");
            mock.override("VAULT_OPEN_TIMEOUT", " 5");
            mock.override("VAULT_READ_TIMEOUT", "30\n");
            mock.override("VAULT_SSL_CERT", pemFile + "\n");

            final var config = new VaultConfig().environmentLoader(mock).build();
            assertEquals("https://127.0.0.1:8200", config.getAddress());
            assertEquals("s.token", new String(config.getToken()));
            assertEquals(Integer.valueOf(5), config.getOpenTimeout());
            assertEquals(Integer.valueOf(30), config.getReadTimeout());
            Assert.assertNotNull(config.getSslConfig().getSslContext());
        } finally {
            Files.delete(pemFile);
        }
    }

    /**
     * A zero timeout from the environment is kept (it means no timeout), a negative one is
     * ignored.
     */
    @Test
    public void testConfigBuilder_LoadFromEnv_ZeroAndNegativeTimeouts() throws VaultException {
        final var mock = new MockEnvironmentLoader();
        mock.override("VAULT_ADDR", "http://127.0.0.1:8200");
        mock.override("VAULT_OPEN_TIMEOUT", "0");
        mock.override("VAULT_READ_TIMEOUT", "-1");

        final var config = new VaultConfig().environmentLoader(mock).build();
        assertEquals(Integer.valueOf(0), config.getOpenTimeout());
        Assert.assertNull(config.getReadTimeout());
    }

    /**
     * <code>VAULT_TOKEN</code> is not looked up when a token was set explicitly, since the default
     * loader may read the <code>~/.vault-token</code> file for it.
     */
    @Test
    public void testConfigBuilder_ExplicitTokenSkipsEnvLookup() throws VaultException {
        final List<String> loaded = new ArrayList<>();
        final MockEnvironmentLoader mock = new MockEnvironmentLoader() {
            @Override
            public String loadVariable(final String name) {
                loaded.add(name);
                return super.loadVariable(name);
            }
        };
        mock.override("VAULT_TOKEN", "from-env");

        final var config = new VaultConfig()
                .environmentLoader(mock)
                .address("http://127.0.0.1:8200")
                .token("explicit")
                .build();
        assertEquals("explicit", new String(config.getToken()));
        Assert.assertFalse(loaded.contains("VAULT_TOKEN"));
    }

    /**
     * An <code>SslConfig</code> that was never built must not disable SSL verification.
     */
    @Test
    public void testUnbuiltSslConfigVerifies() {
        Assert.assertTrue(new SslConfig().isVerify());
    }

    /**
     * An explicit <code>verify()</code> value is honored whether or not <code>build()</code> was
     * called, and also when set after it.
     */
    @Test
    public void testSslVerifyExplicitValueAlwaysWins() throws VaultException {
        Assert.assertFalse(new SslConfig().verify(false).isVerify());
        Assert.assertFalse(new SslConfig().build().verify(false).isVerify());

        final var mock = new MockEnvironmentLoader();
        mock.override("VAULT_SSL_VERIFY", "false");
        Assert.assertTrue(new SslConfig().environmentLoader(mock).build().verify(true).isVerify());
    }

    /**
     * <code>VaultConfig.build()</code> builds an <code>SslConfig</code> whose own
     * <code>build()</code> was not called, so a supplied certificate is actually used.
     */
    @Test
    public void testBuildBuildsPassedSslConfig() throws VaultException {
        final var sslConfig = new SslConfig().pemResource("/cert.pem");
        Assert.assertNull(sslConfig.getSslContext());

        final var config = new VaultConfig()
                .environmentLoader(new MockEnvironmentLoader())
                .address("https://127.0.0.1:8200")
                .sslConfig(sslConfig)
                .build();
        Assert.assertTrue(config.getSslConfig().isVerify());
        Assert.assertNotNull(sslConfig.getSslContext());
    }

    /**
     * A passed <code>SslConfig</code> without its own environment loader uses the one of the
     * <code>VaultConfig</code>.
     */
    @Test
    public void testBuildPassesEnvironmentLoaderToSslConfig() throws VaultException {
        final var mock = new MockEnvironmentLoader();
        mock.override("VAULT_SSL_VERIFY", "false");
        final var config = new VaultConfig()
                .environmentLoader(mock)
                .address("https://127.0.0.1:8200")
                .sslConfig(new SslConfig())
                .build();
        Assert.assertFalse(config.getSslConfig().isVerify());
    }

    /**
     * An already built <code>SslConfig</code> is left untouched.
     */
    @Test
    public void testBuildDoesNotRebuildBuiltSslConfig() throws VaultException {
        final var sslEnv = new MockEnvironmentLoader();
        sslEnv.override("VAULT_SSL_VERIFY", "false");
        final var sslConfig = new SslConfig().environmentLoader(sslEnv).build();

        final var vaultEnv = new MockEnvironmentLoader();
        vaultEnv.override("VAULT_SSL_VERIFY", "true");
        final var config = new VaultConfig()
                .environmentLoader(vaultEnv)
                .address("https://127.0.0.1:8200")
                .sslConfig(sslConfig)
                .build();
        Assert.assertFalse(config.getSslConfig().isVerify());

        final var pemConfig = new SslConfig().pemResource("/cert.pem").build();
        final var sslContext = pemConfig.getSslContext();
        new VaultConfig()
                .environmentLoader(new MockEnvironmentLoader())
                .address("https://127.0.0.1:8200")
                .sslConfig(pemConfig)
                .build();
        Assert.assertSame(sslContext, pemConfig.getSslContext());
    }

    /**
     * An invalid certificate in a passed <code>SslConfig</code> is reported by
     * <code>VaultConfig.build()</code>.
     */
    @Test(expected = VaultException.class)
    public void testBuildFailsOnInvalidPassedSslConfig() throws VaultException {
        new VaultConfig()
                .environmentLoader(new MockEnvironmentLoader())
                .address("https://127.0.0.1:8200")
                .sslConfig(new SslConfig().pemUTF8("not a certificate"))
                .build();
    }

    /**
     * An environment loader with no variables set, that can be serialized (unlike the inner
     * <code>MockEnvironmentLoader</code>, which holds a reference to the test instance).
     */
    static class EmptyEnvironmentLoader extends EnvironmentLoader {

        @Override
        public String loadVariable(final String name) {
            return null;
        }
    }

    /**
     * A <code>VaultConfig</code> with a preconfigured <code>HttpClient</code> (which is not
     * serializable) can be serialized. The deserialized copy falls back to its own default client.
     */
    @Test
    public void testSerializationWithCustomHttpClient() throws Exception {
        final HttpClient httpClient = HttpClient.newHttpClient();
        final VaultConfig config = new VaultConfig()
                .environmentLoader(new EmptyEnvironmentLoader())
                .address("http://127.0.0.1:8200")
                .token("token")
                .httpClient(httpClient)
                .build();

        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(config);
        }
        final VaultConfig copy;
        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            copy = (VaultConfig) in.readObject();
        }

        assertEquals("http://127.0.0.1:8200", copy.getAddress());
        Assert.assertNotNull(copy.getHttpClient());
        Assert.assertNotSame(httpClient, copy.getHttpClient());
        Assert.assertSame(copy.getHttpClient(), copy.getHttpClient());
    }

    /**
     * The default HttpClient is reused while its settings are unchanged, and rebuilt when the
     * passed <code>SslConfig</code> is changed after <code>VaultConfig.build()</code>.
     */
    @Test
    public void testDefaultHttpClientFollowsSslConfigChanges() throws Exception {
        final String pem;
        try (InputStream input = this.getClass().getResourceAsStream("/cert.pem")) {
            pem = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        final SslConfig sslConfig = new SslConfig().verify(false);
        final VaultConfig config = new VaultConfig()
                .environmentLoader(new MockEnvironmentLoader())
                .address("https://127.0.0.1:8200")
                .sslConfig(sslConfig)
                .build();

        final HttpClient unverified = config.getHttpClient();
        Assert.assertSame(unverified, config.getHttpClient());

        // Verification turned on, with new certificates: build() creates a new SSLContext
        sslConfig.verify(true).pemUTF8(pem).build();
        final HttpClient withPem = config.getHttpClient();
        Assert.assertNotSame(unverified, withPem);
        Assert.assertSame(sslConfig.getSslContext(), withPem.sslContext());
        Assert.assertSame(withPem, config.getHttpClient());

        // Verification turned off, without calling build() again
        sslConfig.verify(false);
        final HttpClient unverifiedAgain = config.getHttpClient();
        Assert.assertNotSame(withPem, unverifiedAgain);
        Assert.assertNotSame(sslConfig.getSslContext(), unverifiedAgain.sslContext());

        // A new connect timeout
        config.openTimeout(5);
        final HttpClient withTimeout = config.getHttpClient();
        Assert.assertNotSame(unverifiedAgain, withTimeout);
        assertEquals(Duration.ofSeconds(5), withTimeout.connectTimeout().orElse(null));
    }

    /**
     * A <code>null</code> or blank address is treated as unset, so <code>VAULT_ADDR</code> is used.
     */
    @Test
    public void testConfigBuilder_NullOrBlankAddressFallsBackToEnv() throws VaultException {
        final MockEnvironmentLoader mock = new MockEnvironmentLoader();
        mock.override("VAULT_ADDR", "http://127.0.0.1:8200");

        assertEquals("http://127.0.0.1:8200",
                new VaultConfig().environmentLoader(mock).address(null).build().getAddress());
        assertEquals("http://127.0.0.1:8200",
                new VaultConfig().environmentLoader(mock).address(" \n").build().getAddress());
    }

    /**
     * A prefix path depth lower than 1 is rejected with a message stating the valid range.
     */
    @Test
    public void testPrefixPathDepthBelowOneIsRejected() {
        try {
            new VaultConfig().prefixPath("/");
            Assert.fail("Expected an IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertEquals("prefixPathDepth must be at least 1, was: 0", e.getMessage());
        }
    }
}
