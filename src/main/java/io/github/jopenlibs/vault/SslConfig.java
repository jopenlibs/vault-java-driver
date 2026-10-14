package io.github.jopenlibs.vault;

import io.github.jopenlibs.vault.api.Auth;
import io.github.jopenlibs.vault.rest.Rest;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

/**
 * <p>A container for SSL-related configuration options, meant to be stored within a
 * {@link VaultConfig} instance.</p>
 *
 * <p>Construct instances of this class using a builder pattern, calling setter methods for each
 * value and then terminating with a call to build().</p>
 */
public class SslConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final String VAULT_SSL_VERIFY = "VAULT_SSL_VERIFY";
    private static final String VAULT_SSL_CERT = "VAULT_SSL_CERT";
    private static final List<String> VERIFY_TRUE_VALUES = List.of("t", "true", "1", "y", "yes", "on");
    private static final Logger LOGGER = Logger.getLogger(SslConfig.class.getCanonicalName());

    private boolean verify = true;
    private transient SSLContext sslContext;
    private transient KeyStore trustStore;
    private transient KeyStore keyStore;
    private String keyStorePassword;
    private String pemUTF8;  // exposed to unit tests
    private String clientPemUTF8;
    private String clientKeyPemUTF8;
    private Boolean verifyObject;
    private EnvironmentLoader environmentLoader;
    private boolean built;

    /**
     * <p>The code used to load environment variables is encapsulated here, so that a mock version
     * of that environment loader can be used by unit tests.</p>
     *
     * <p>This method is used by unit tests, to inject a mock environment variable when
     * constructing a <code>SslConfig</code> instance using the builder pattern approach rather than
     * the convenience constructor. There really shouldn't ever be a need to call this method
     * outside of a unit test context (hence the <code>protected</code> access level).</p>
     *
     * @param environmentLoader An environment variable loader implementation (presumably a mock)
     * @return This object, with environmentLoader populated, ready for additional builder-pattern
     * method calls or else finalization with the build() method
     */
    protected SslConfig environmentLoader(final EnvironmentLoader environmentLoader) {
        this.environmentLoader = environmentLoader;
        return this;
    }

    /**
     * <p>Whether or not HTTPS connections to the Vault server should verify that a valid SSL
     * certificate is being used.  Unless this is set to <code>false</code>, the default behavior is
     * to always verify SSL certificates.</p>
     *
     * <p>SSL CERTIFICATE VERIFICATION SHOULD NOT BE DISABLED IN PRODUCTION!  This feature is made
     * available to facilitate development or testing environments, where you might be using a
     * self-signed cert that will not pass verification.  However, even if you are using a
     * self-signed cert on your Vault server, you can still leave SSL verification enabled and have
     * your application supply the cert using <code>pemFile()</code>,<code>pemResource()</code>, or
     * <code>pemUTF8()</code>.</p>
     *
     * <p>If no verify is explicitly set, either by this method in a builder pattern approach or
     * else by one of the convenience constructors, then <code>SslConfig</code> will look to the
     * <code>VAULT_SSL_VERIFY</code> environment variable. Only the value <code>false</code>
     * (case-insensitive) disables verification; any other value keeps it enabled.</p>
     *
     * @param verify Whether or not to verify the SSL certificate used by Vault with HTTPS
     * connections.  Default is <code>true</code>.
     * @return This object, with verify populated, ready for additional builder-pattern method calls
     * or else finalization with the build() method
     */
    public SslConfig verify(final Boolean verify) {
        this.verifyObject = verify;
        return this;
    }

    /**
     * <p>A Java keystore, containing a client certificate that's registered with Vault's TLS
     * Certificate auth backend. If you are not using certificate based client auth, then this field
     * may remain un-set.</p>
     *
     * <p>Note that you cannot mix-and-match JKS based config with PEM based config.  If any of the
     * keyStore or trustStore setters are used, then the build() method will complete ignore any PEM
     * data that was set.</p>
     *
     * @param keyStore A keystore, containing a client certificate registered with Vault's TLS
     * Certificate auth backend
     * @param password The password needed to access the keystore (can be <code>null</code>)
     * @return This object, with keyStore and keyStorePassword populated, ready for additional
     * builder-pattern method calls or else finalization with the build() method
     */
    public SslConfig keyStore(final KeyStore keyStore, final String password) {
        this.keyStore = keyStore;
        this.keyStorePassword = password;
        return this;
    }

    /**
     * <p>A Java keystore, containing a client certificate that's registered with Vault's TLS
     * Certificate auth backend. If you are not using certificate based client auth, then this field
     * may remain un-set.  This method loads the keystore from a JKS file on the filesystem.</p>
     *
     * <p>Note that you cannot mix-and-match JKS based config with PEM based config.  If any of the
     * keyStore or trustStore setters are used, then the build() method will complete ignore any PEM
     * data that was set.</p>
     *
     * @param keyStoreFile A JKS keystore file, containing a client certificate registered with
     * Vault's TLS Certificate auth backend
     * @param password The password needed to access the keystore (can be <code>null</code>)
     * @return This object, with keyStore and keyStorePassword populated, ready for additional
     * builder-pattern method calls or else finalization with the build() method
     * @throws VaultException If any error occurs while loading the keystore
     */
    public SslConfig keyStoreFile(final File keyStoreFile, final String password)
            throws VaultException {
        try (final InputStream inputStream = new FileInputStream(keyStoreFile)) {
            this.keyStore = inputStreamToKeyStore(inputStream, password);
            this.keyStorePassword = password;
            return this;
        } catch (IOException e) {
            throw new VaultException(e);
        }
    }

    /**
     * <p>A Java keystore, containing a client certificate that's registered with Vault's TLS
     * Certificate auth backend. If you are not using certificate based client auth, then this field
     * may remain un-set.  This method loads the keystore from a classpath resource (e.g. you've
     * bundled the JKS file into your library or application's JAR/WAR/EAR file).</p>
     *
     * <p>Note that you cannot mix-and-match JKS based config with PEM based config.  If any of the
     * keyStore or trustStore setters are used, then the build() method will complete ignore any PEM
     * data that was set.</p>
     *
     * @param classpathResource A JKS keystore file, containing a client certificate registered with
     * Vault's TLS Certificate auth backend
     * @param password The password needed to access the keystore (can be <code>null</code>)
     * @return This object, with keyStore and keyStorePassword populated, ready for additional
     * builder-pattern method calls or else finalization with the build() method
     * @throws VaultException If any error occurs while loading the keystore
     */
    public SslConfig keyStoreResource(final String classpathResource, final String password)
            throws VaultException {
        try (final InputStream inputStream = this.getClass()
                .getResourceAsStream(classpathResource)) {
            this.keyStore = inputStreamToKeyStore(inputStream, password);
            this.keyStorePassword = password;
            return this;
        } catch (IOException e) {
            throw new VaultException(e);
        }
    }

    /**
     * <p>A Java keystore, containing the X509 certificate used by Vault.  Used by the driver to
     * trust SSL connections from the server using this cert.</p>
     *
     * <p>Note that you cannot mix-and-match JKS based config with PEM based config.  If any of the
     * keyStore or trustStore setters are used, then the build() method will complete ignore any PEM
     * data that was set.</p>
     *
     * @param trustStore A truststore, containing the Vault server's X509 certificate
     * @return This object, with trustStore populated, ready for additional builder-pattern method
     * calls or else finalization with the build() method
     */
    public SslConfig trustStore(final KeyStore trustStore) {
        this.trustStore = trustStore;
        return this;
    }

    /**
     * <p>A Java keystore, containing the X509 certificate used by Vault.  Used by the driver to
     * trust SSL connections from the server using this cert.  This method load the truststore from
     * a JKS file on the filesystem.</p>
     *
     * <p>Note that you cannot mix-and-match JKS based config with PEM based config.  If any of the
     * keyStore or trustStore setters are used, then the build() method will complete ignore any PEM
     * data that was set.</p>
     *
     * @param trustStoreFile A JKS truststore file, containing the Vault server's X509 certificate
     * @return This object, with trustStore populated, ready for additional builder-pattern method
     * calls or else finalization with the build() method
     * @throws VaultException If any error occurs while loading the truststore
     */
    public SslConfig trustStoreFile(final File trustStoreFile) throws VaultException {
        try (final InputStream inputStream = new FileInputStream(trustStoreFile)) {
            this.trustStore = inputStreamToKeyStore(inputStream, null);
            return this;
        } catch (IOException e) {
            throw new VaultException(e);
        }
    }

    /**
     * <p>A Java keystore, containing the X509 certificate used by Vault.  Used by the driver to
     * trust SSL connections from the server using this cert.  This method load the truststore from
     * a classpath resource (e.g. you've bundled the JKS file into your library or application's
     * JAR/WAR/EAR file).</p>
     *
     * <p>Note that you cannot mix-and-match JKS based config with PEM based config.  If any of the
     * keyStore or trustStore setters are used, then the build() method will complete ignore any PEM
     * data that was set.</p>
     *
     * @param classpathResource A JKS truststore file, containing the Vault server's X509
     * certificate
     * @return This object, with trustStore populated, ready for additional builder-pattern method
     * calls or else finalization with the build() method
     * @throws VaultException If any error occurs while loading the truststore
     */
    public SslConfig trustStoreResource(final String classpathResource) throws VaultException {
        try (final InputStream inputStream = this.getClass()
                .getResourceAsStream(classpathResource)) {
            this.trustStore = inputStreamToKeyStore(inputStream, null);
            return this;
        } catch (IOException e) {
            throw new VaultException(e);
        }
    }

    /**
     * <p>An X.509 certificate, to use when communicating with Vault over HTTPS.  This method
     * accepts a string containing the certificate data.  This string should meet the following
     * requirements:</p>
     *
     * <ul>
     *     <li>Contain an unencrypted X.509 certificate, in PEM format.</li>
     *     <li>Use UTF-8 encoding.</li>
     *     <li>
     *          Contain a line-break between the certificate header
     *          (e.g. "-----BEGIN CERTIFICATE-----") and the rest of the certificate content. It
     *          doesn't matter whether or not there are additional line breaks within the
     *          certificate content, or whether there is a line break before the certificate
     *          footer (e.g. "-----END CERTIFICATE-----").  But the Java standard library will fail
     *          to properly process the certificate without a break following the header (see
     *          <a href="http://www.doublecloud.org/2014/03/reading-x-509-certificate-in-java-how-to-handle-format-issue/">http://www.doublecloud.org/2014/03/reading-x-509-certificate-in-java-how-to-handle-format-issue/</a>).
     *      </li>
     * </ul>
     *
     * <p>If no certificate data is provided, either by this method or <code>pemFile()</code>
     * or <code>pemResource()</code>, then <code>SslConfig</code> will look to the
     * <code>VAULT_SSL_CERT</code>environment variable.</p>
     *
     * @param pemUTF8 An X.509 certificate, in unencrypted PEM format with UTF-8 encoding.
     * @return This object, with pemUTF8 populated, ready for additional builder-pattern method
     * calls or else finalization with the build() method
     */
    public SslConfig pemUTF8(final String pemUTF8) {
        this.pemUTF8 = pemUTF8;
        return this;
    }

    /**
     * <p>An X.509 certificate, to use when communicating with Vault over HTTPS.  This method
     * accepts the path of a file containing the certificate data.  This file's contents should meet
     * the following requirements:</p>
     *
     * <ul>
     *     <li>Contain an unencrypted X.509 certificate, in PEM format.</li>
     *     <li>Use UTF-8 encoding.</li>
     *     <li>
     *          Contain a line-break between the certificate header (e.g.
     *          "-----BEGIN CERTIFICATE-----") and the rest of the certificate content.  It doesn't
     *          matter whether or not there are additional line breaks within the certificate
     *          content, or whether there is a line break before the certificate footer (e.g.
     *          "-----END CERTIFICATE-----").  But the Java standard library will fail to properly
     *          process the certificate without a break following the header (see
     *          <a href="http://www.doublecloud.org/2014/03/reading-x-509-certificate-in-java-how-to-handle-format-issue/">http://www.doublecloud.org/2014/03/reading-x-509-certificate-in-java-how-to-handle-format-issue/</a>).
     *      </li>
     * </ul>
     *
     * <p>If no certificate data is provided, either by this method or <code>pemResource()</code>
     * or <code>pemUTF8()</code>, then <code>SslConfig</code> will look to the
     * <code>VAULT_SSL_CERT</code>environment variable.</p>
     *
     * @param pemFile The path of a file containing an X.509 certificate, in unencrypted PEM format
     * with UTF-8 encoding.
     * @return This object, with pemUTF8 populated, ready for additional builder-pattern method
     * calls or else finalization with the build() method
     * @throws VaultException If any error occurs while loading and parsing the PEM file
     */
    public SslConfig pemFile(final File pemFile) throws VaultException {
        try {
            this.pemUTF8 = Files.readString(pemFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new VaultException(e);
        }
        return this;
    }

    /**
     * <p>An X.509 certificate, to use when communicating with Vault over HTTPS.  This method
     * accepts the path of a classpath resource containing the certificate data (e.g. you've bundled
     * the cert into your library or application's JAR/WAR/EAR file).  This resource's contents
     * should meet the following requirements:</p>
     *
     * <ul>
     *     <li>Contain an unencrypted X.509 certificate, in PEM format.</li>
     *     <li>Use UTF-8 encoding.</li>
     *     <li>
     *          Contain a line-break between the certificate header (e.g.
     *          "-----BEGIN CERTIFICATE-----") and the rest of the certificate content.  It doesn't
     *          matter whether or not there are additional line breaks within the certificate
     *          content, or whether there is a line break before the certificate footer (e.g.
     *          "-----END CERTIFICATE-----").  But the Java standard library will fail to properly
     *          process the certificate without a break following the header (see
     *          <a href="http://www.doublecloud.org/2014/03/reading-x-509-certificate-in-java-how-to-handle-format-issue/">http://www.doublecloud.org/2014/03/reading-x-509-certificate-in-java-how-to-handle-format-issue/</a>).
     *      </li>
     * </ul>
     *
     * <p>If no certificate data is provided, either by this method or <code>pemFile()</code>
     * or <code>pemUTF8()</code>, then <code>SslConfig</code> will look to the
     * <code>VAULT_SSL_CERT</code>environment variable.</p>
     *
     * @param classpathResource The path of a classpath resource containing an X.509 certificate, in
     * unencrypted PEM format with UTF-8 encoding.
     * @return This object, with pemUTF8 populated, ready for additional builder-pattern method
     * calls or else finalization with the build() method
     * @throws VaultException If any error occurs while loading and parsing the PEM file
     */
    public SslConfig pemResource(final String classpathResource) throws VaultException {
        try (final InputStream input = this.getClass().getResourceAsStream(classpathResource)) {
            this.pemUTF8 = inputStreamToUTF8(input);
        } catch (IOException e) {
            throw new VaultException(e);
        }
        return this;
    }

    /**
     * <p>An X.509 client certificate, for use with Vault's TLS Certificate auth backend.  This
     * string should meet the same formatting requirements as pemUTF8(String).</p>
     *
     * @param clientPemUTF8 An X.509 client certificate, in unencrypted PEM format with UTF-8
     * encoding.
     * @return This object, with clientPemUTF8 populated, ready for additional builder-pattern
     * method calls or else finalization with the build() method
     * @see Auth#loginByCert()
     */
    public SslConfig clientPemUTF8(final String clientPemUTF8) {
        this.clientPemUTF8 = clientPemUTF8;
        return this;
    }

    /**
     * <p>An X.509 client certificate, for use with Vault's TLS Certificate auth backend.  This
     * method accepts the path of a file containing the certificate data.  This file should meet the
     * same requirements as pemFile(File).</p>
     *
     * @param clientPemFile The path of a file containing an X.509 certificate, in unencrypted PEM
     * format with UTF-8 encoding.
     * @return This object, with clientPemUTF8 populated, ready for additional builder-pattern
     * method calls or else finalization with the build() method
     * @throws VaultException If any error occurs while loading and parsing the PEM file
     * @see Auth#loginByCert()
     */
    public SslConfig clientPemFile(final File clientPemFile) throws VaultException {
        try {
            this.clientPemUTF8 = Files.readString(clientPemFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new VaultException(e);
        }
        return this;
    }

    /**
     * <p>An X.509 certificate, for use with Vault's TLS Certificate auth backend.  This method
     * accepts the path of a classpath resource containing the certificate data (e.g. you've bundled
     * the cert into your library or application's JAR/WAR/EAR file).  This resource's contents
     * should meet the same requirements as pemResource(String).</p>
     *
     * @param classpathResource The path of a classpath resource containing an X.509 certificate, in
     * unencrypted PEM format with UTF-8 encoding.
     * @return This object, with clientPemUTF8 populated, ready for additional builder-pattern
     * method calls or else finalization with the build() method
     * @throws VaultException If any error occurs while loading and parsing the PEM file
     * @see Auth#loginByCert()
     */
    public SslConfig clientPemResource(final String classpathResource) throws VaultException {
        try (final InputStream input = this.getClass().getResourceAsStream(classpathResource)) {
            this.clientPemUTF8 = inputStreamToUTF8(input);
        } catch (IOException e) {
            throw new VaultException(e);
        }
        return this;
    }

    /**
     * <p>An RSA private key, for use with Vault's TLS Certificate auth backend.  The string should
     * meet the following requirements:</p>
     *
     * <ul>
     *     <li>Contain an unencrypted RSA private key, in PEM format.</li>
     *     <li>Use UTF-8 encoding.</li>
     * </ul>
     *
     * @param clientKeyPemUTF8 An RSA private key, in unencrypted PEM format with UTF-8 encoding.
     * @return This object, with clientKeyPemUTF8 populated, ready for additional builder-pattern
     * method calls or else finalization with the build() method
     */
    public SslConfig clientKeyPemUTF8(final String clientKeyPemUTF8) {
        this.clientKeyPemUTF8 = clientKeyPemUTF8;
        return this;
    }

    /**
     * <p>An RSA private key, for use with Vault's TLS Certificate auth backend.  This method
     * accepts the path of a file containing the private key data.  This file's contents should meet
     * the following requirements:</p>
     *
     * <ul>
     *     <li>Contain an unencrypted RSA private key, in PEM format.</li>
     *     <li>Use UTF-8 encoding.</li>
     * </ul>
     *
     * @param clientKeyPemFile The path of a file containing an RSA private key, in unencrypted PEM
     * format with UTF-8 encoding.
     * @return This object, with clientKeyPemUTF8 populated, ready for additional builder-pattern
     * method calls or else finalization with the build() method
     * @throws VaultException If any error occurs while loading and parsing the PEM file
     */
    public SslConfig clientKeyPemFile(final File clientKeyPemFile) throws VaultException {
        try {
            this.clientKeyPemUTF8 = Files.readString(clientKeyPemFile.toPath(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new VaultException(e);
        }
        return this;
    }

    /**
     * <p>An RSA private key, for use with Vault's TLS Certificate auth backend.  This method
     * accepts the path of a classpath resource containing the private key data (e.g. you've bundled
     * the private key into your library or application's JAR/WAR/EAR file).  This file's contents
     * should meet the following requirements:</p>
     *
     * <ul>
     *     <li>Contain an unencrypted RSA private key, in PEM format.</li>
     *     <li>Use UTF-8 encoding.</li>
     * </ul>
     *
     * @param classpathResource The path of a classpath resource containing an RSA private key, in
     * unencrypted PEM format with UTF-8 encoding.
     * @return This object, with clientKeyPemUTF8 populated, ready for additional builder-pattern
     * method calls or else finalization with the build() method
     * @throws VaultException If any error occurs while loading and parsing the PEM file
     */
    public SslConfig clientKeyPemResource(final String classpathResource) throws VaultException {
        try (final InputStream input = this.getClass().getResourceAsStream(classpathResource)) {
            this.clientKeyPemUTF8 = inputStreamToUTF8(input);
        } catch (IOException e) {
            throw new VaultException(e);
        }
        return this;
    }

    /**
     * <p>This is the terminating method in the builder pattern.  The method that validates all of
     * the fields that has been set already, uses environment variables when available to populate
     * any unset fields, and returns a <code>SslConfig</code> object that is ready for use.</p>
     *
     * @return This object, with all available config options parsed and loaded
     * @throws VaultException If SSL certificate verification is enabled, and any problem occurs
     * while trying to build an SSLContext
     */
    public SslConfig build() throws VaultException {
        if (this.environmentLoader == null) {
            this.environmentLoader = new EnvironmentLoader();
        }
        if (this.verifyObject != null) {
            this.verify = verifyObject;
        } else {
            this.verify = parseVerify(EnvironmentLoader.load(environmentLoader, VAULT_SSL_VERIFY));
        }
        if (this.verify && this.pemUTF8 == null) {
            final String pemPath = EnvironmentLoader.load(environmentLoader, VAULT_SSL_CERT);
            if (pemPath != null) {
                try {
                    this.pemUTF8 = Files.readString(new File(pemPath).toPath(),
                            StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new VaultException(e);
                }
            }
        }
        buildSsl();
        this.built = true;
        return this;
    }

    /**
     * Builds this object, unless {@link #build()} was already called. Used by
     * {@link VaultConfig#build()}, so that a <code>SslConfig</code> passed to it is ready for use
     * even if its own <code>build()</code> was not called.
     *
     * @param defaultEnvironmentLoader The environment loader to use, if none was set on this object
     * @return This object
     * @throws VaultException If any problem occurs while building
     */
    SslConfig buildIfNeeded(final EnvironmentLoader defaultEnvironmentLoader)
            throws VaultException {
        if (built) {
            return this;
        }
        if (this.environmentLoader == null) {
            this.environmentLoader = defaultEnvironmentLoader;
        }
        return build();
    }

    /**
     * Parses the <code>VAULT_SSL_VERIFY</code> environment variable, ignoring case and surrounding
     * whitespace. Verification is only disabled by an explicit <code>false</code>, so that a typo
     * or an unexpected value can never silently turn it off. <code>t</code>, <code>true</code>,
     * <code>1</code>, <code>y</code>, <code>yes</code> and <code>on</code> enable it; any other
     * value also enables it, with a warning.
     *
     * @param value The variable value, or <code>null</code> if unset
     * @return <code>false</code> only if the value is <code>false</code>
     */
    static boolean parseVerify(final String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        final String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (normalized.equals("false")) {
            return false;
        }
        if (!VERIFY_TRUE_VALUES.contains(normalized)) {
            LOGGER.warning(String.format(
                    "The %s environment variable contains value \"%s\", which is neither \"false\" nor one of %s. SSL verification stays enabled.",
                    VAULT_SSL_VERIFY, value, VERIFY_TRUE_VALUES));
        }
        return true;
    }

    /**
     * <p>Whether HTTPS connections should verify the Vault server's SSL certificate.</p>
     *
     * <p>A value set with {@link #verify(Boolean)} always takes priority, even if it was set after
     * {@link #build()}. Otherwise this is the value resolved by {@link #build()} (from the
     * <code>VAULT_SSL_VERIFY</code> environment variable), or <code>true</code> if
     * {@link #build()} was not called.</p>
     *
     * @return <code>false</code> only if verification was explicitly disabled
     */
    public boolean isVerify() {
        if (verifyObject != null) {
            return verifyObject;
        }
        return verify;
    }

    public SSLContext getSslContext() {
        return sslContext;
    }

    protected String getPemUTF8() {
        return pemUTF8;
    }

    /**
     * <p>Constructs the {@link this#sslContext} member field, if SSL verification is enabled and
     * any JKS or PEM-based data was populated.  This method is broken off from
     * {@link this#build()}, because the same process must occur both at build time, and when an
     * <code>SslConfig</code> instance is deserialized (see
     * {@link this#readObject(ObjectInputStream)}.</p>
     *
     * <p>This SSLContext object will be passed to the {@link Rest} layer, where it will be used
     * when establishing an HTTPS connection to provide access to trusted server X509 certificates
     * (as well as client certificates and private keys when TLS client auth is used).</p>
     */
    private void buildSsl() throws VaultException {
        if (verify) {
            if (keyStore != null || trustStore != null) {
                this.sslContext = buildSslContextFromJks();
            } else if (pemUTF8 != null || clientPemUTF8 != null || clientKeyPemUTF8 != null) {
                this.sslContext = buildSslContextFromPem();
            }
        }
    }

    /**
     * Constructs an SSLContext, when keystore and/or truststore data was provided in JKS format.
     *
     * @return An SSLContext, constructed with the JKS data supplied.
     */
    private SSLContext buildSslContextFromJks() throws VaultException {
        TrustManager[] trustManagers = null;
        if (trustStore != null) {
            try {
                final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                        TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(trustStore);
                trustManagers = trustManagerFactory.getTrustManagers();
            } catch (NoSuchAlgorithmException | KeyStoreException e) {
                throw new VaultException(e);
            }
        }

        KeyManager[] keyManagers = null;
        if (keyStore != null) {
            try {
                final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
                        KeyManagerFactory.getDefaultAlgorithm());
                keyManagerFactory.init(keyStore,
                        keyStorePassword == null ? null : keyStorePassword.toCharArray());
                keyManagers = keyManagerFactory.getKeyManagers();
            } catch (NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException e) {
                throw new VaultException(e);
            }
        }

        try {
            final SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
            sslContext.init(keyManagers, trustManagers, null);
            return sslContext;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new VaultException(e);
        }
    }

    /**
     * Constructs an SSLContext, when server and/or client cert data was provided in JKS format.
     *
     * @return An SSLContext, constructed with the PEM data supplied.
     */
    private SSLContext buildSslContextFromPem() throws VaultException {
        try {
            final CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");

            TrustManager[] trustManagers = null;
            if (pemUTF8 != null) {
                final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                        TrustManagerFactory.getDefaultAlgorithm());
                // Convert the trusted servers PEM data into an X509Certificate
                X509Certificate certificate;
                try (final ByteArrayInputStream pem = new ByteArrayInputStream(
                        pemUTF8.getBytes(StandardCharsets.UTF_8))) {
                    certificate = (X509Certificate) certificateFactory.generateCertificate(pem);
                }
                // Build a truststore
                final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                keyStore.load(null);
                keyStore.setCertificateEntry("caCert", certificate);
                trustManagerFactory.init(keyStore);
                trustManagers = trustManagerFactory.getTrustManagers();
            }
            KeyManager[] keyManagers = null;
            if (clientPemUTF8 != null && clientKeyPemUTF8 != null) {
                final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
                        KeyManagerFactory.getDefaultAlgorithm());
                // Convert the client certificate PEM data into an X509Certificate
                X509Certificate clientCertificate;
                try (final ByteArrayInputStream pem = new ByteArrayInputStream(
                        clientPemUTF8.getBytes(StandardCharsets.UTF_8))) {
                    clientCertificate = (X509Certificate) certificateFactory.generateCertificate(
                            pem);
                }

                // Convert the client private key into a PrivateKey
                final String strippedKey = clientKeyPemUTF8.replace("-----BEGIN PRIVATE KEY-----",
                                "")
                        .replace("-----END PRIVATE KEY-----", "");
                final byte[] keyBytes = Base64.getMimeDecoder().decode(strippedKey);
                final var pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(keyBytes);
                final KeyFactory factory = KeyFactory.getInstance("RSA");
                final PrivateKey privateKey = factory.generatePrivate(pkcs8EncodedKeySpec);

                // Build a keystore
                final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                keyStore.load(null, "password".toCharArray());
                keyStore.setCertificateEntry("clientCert", clientCertificate);
                keyStore.setKeyEntry("key", privateKey, "password".toCharArray(),
                        new Certificate[]{clientCertificate});
                keyManagerFactory.init(keyStore, "password".toCharArray());
                keyManagers = keyManagerFactory.getKeyManagers();
            }

            final SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
            sslContext.init(keyManagers, trustManagers, null);
            return sslContext;
        } catch (CertificateException | IOException | NoSuchAlgorithmException | KeyStoreException |
                 KeyManagementException | UnrecoverableKeyException | InvalidKeySpecException e) {
            throw new VaultException(e);
        }
    }

    /**
     * <p>A utility method for loading a JKS formatted {@link KeyStore} from an
     * {@link InputStream}, presumably representing a file on the filesystem or a classpath
     * resource.</p>
     *
     * <p>Note that this method does not close the InputStream when finished.  That responsibility
     * falls to the caller.</p>
     *
     * @param inputStream An InputStream of JKS file content
     * @param password The password, if any, needed to open this JKS file (can be
     * <code>null</code>)
     */
    private KeyStore inputStreamToKeyStore(final InputStream inputStream, final String password)
            throws VaultException {
        try {
            final KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(inputStream, password == null ? null : password.toCharArray());
            return keyStore;
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException |
                 IOException e) {
            throw new VaultException(e);
        }
    }

    /**
     * A utility method for extracting content from an {@link InputStream} into a UTF-8 encoded
     * {@link String}.  Used by the various methods in this class that load PEM data from files or
     * classpath resources.
     *
     * @param input An InputStream, presumably containing PEM data with UTF-8 encoding
     * @return A UTF-8 encoded String, containing all of the InputStream's content
     */
    private static String inputStreamToUTF8(final InputStream input) throws IOException {
        final var utf8 = new StringBuilder();
        try (final BufferedReader in = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String str;
            while ((str = in.readLine()) != null) {
                // String concatenation is less efficient, but for some reason the line-breaks (which are necessary
                // for Java to correctly parse SSL certs) are stripped off when using a StringBuilder.
                utf8.append(str).append(System.lineSeparator());
            }
        }
        return utf8.toString();
    }

    /**
     * <p>There was a community request to make {@link VaultImpl} and its config class serializable
     * (<a
     * href="https://github.com/BetterCloud/vault-java-driver/pull/51">https://github.com/BetterCloud/vault-java-driver/pull/51</a>).
     * However, this SslConfig class now contains a member field of type {@link SSLContext}, which
     * cannot be serialized.</p>
     *
     * <p>Therefore, that member field is declared <code>transient</code>.  This means that if an
     * SslConfig object is serialized, its member field will be <code>null</code> after
     * deserialization.  Fortunately, the Java deserialization process provides this lifecycle hook,
     * which is used here to re-populate the sslContext member field.</p>
     *
     * @param in The object being deserialized
     * @throws IOException If an error occurs during deserialization (part of the default Java
     * process)
     * @throws ClassNotFoundException If an error occurs during deserialization (part of the default
     * Java process)
     * @see Serializable
     */
    private void readObject(java.io.ObjectInputStream in)
            throws IOException, ClassNotFoundException {//NOPMD
        // Restore the serialized fields first: buildSsl() depends on them (e.g. verify, pemUTF8)
        in.defaultReadObject();
        // Field initializers don't run on deserialization, so a stream written by an older release
        // (which has no "built" field) or by a never-built instance may carry verify=false without
        // it ever being requested. Unless explicitly disabled, verification stays enabled.
        if (!built && verifyObject == null) {
            this.verify = true;
        }
        try {
            buildSsl();
        } catch (VaultException e) {
            throw new IOException(e);
        }
    }
}
