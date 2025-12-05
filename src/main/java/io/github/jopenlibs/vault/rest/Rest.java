package io.github.jopenlibs.vault.rest;

import io.github.jopenlibs.vault.SslConfig;
import io.github.jopenlibs.vault.VaultConfig;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.TreeMap;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

/**
 * <p>A simple client for issuing HTTP requests.  Supports the HTTP verbs:</p>
 * <ul>
 * <li>GET</li>
 * <li>POST</li>
 * <li>PUT</li>
 * <li>DELETE</li>
 * </ul>
 *
 * <p><code>Rest</code> uses the Builder pattern to provide a basic DSL for usage.  Methods for
 * configuring an HTTP request (i.e. <code>url()</code>, <code>parameter()</code>, and
 * <code>header()</code>) are designed to be chained together, while methods corresponding to the
 * HTTP verbs are terminating operations:</p>
 *
 * <blockquote>
 * <pre>{@code
 * final RestResponse getResponse = new Rest()
 *                              .url("https://httpbin.org/get")
 *                              .header("header-1", "foo")
 *                              .header("header-2", "bar")
 *                              .parameter("param-1", "up")
 *                              .parameter("param-2", "down")
 *                              .get();
 *
 * final RestResponse postResponse = new Rest()
 *                              .url("https://httpbin.org/post")
 *                              .header("header-1", "foo")
 *                              .header("header-2", "bar")
 *                              .body( jsonString.getBytes("UTF-8") )
 *                              .post();
 * }</pre>
 * </blockquote>
 *
 * <p>Header and parameter names and values are url-encoded by the Rest client prior to sending the
 * request. The URL string should be url-encoded by you (if necessary) prior to passing it.</p>
 */
public class Rest {

    /**
     * A dummy SSLContext, for use when SSL verification is disabled.  Overwrites Java's default
     * server certificate verification process, to always trust any certificates.
     */
    private static SSLContext DISABLED_SSL_CONTEXT;

    static {
        try {
            DISABLED_SSL_CONTEXT = SSLContext.getInstance("TLSv1.2");
            DISABLED_SSL_CONTEXT.init(null, new TrustManager[]{new X509ExtendedTrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType,
                        Socket socket) throws CertificateException {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType,
                        Socket socket) throws CertificateException {
                }

                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType,
                        SSLEngine engine) throws CertificateException {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType,
                        SSLEngine engine) throws CertificateException {
                }

                @Override
                public void checkClientTrusted(final X509Certificate[] x509Certificates,
                        final String s) throws CertificateException {
                }

                @Override
                public void checkServerTrusted(final X509Certificate[] x509Certificates,
                        final String s) throws CertificateException {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }}, new java.security.SecureRandom());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }
    }

    private String urlString;
    private byte[] body;
    private final Map<String, String> parameters = new TreeMap<>();
    private final Map<String, String> headers = new TreeMap<>();

    private Integer connectTimeoutSeconds;
    private Integer readTimeoutSeconds;
    private Boolean sslVerification;
    private SSLContext sslContext;
    private final HttpClient configuredClient;

    public Rest(HttpClient configuredClient) {
        this.configuredClient = configuredClient;
    }

    public Rest() {
        this(null);
    }

    /**
     * <p>Sets the base URL to which the HTTP request will be sent.  The URL may or may not include
     * query parameters (e.g. <code>http://httpbin.org/get?param-1=foo</code>).</p>
     *
     * <p>Depending on which HTTP verb is ultimately used, than any additional parameters set via
     * the <code>parameters()</code> method may be appending to this URL.</p>
     *
     * <p>Either way, the responsibility for any url-encoding of this base URL string belongs to
     * the caller.</p>
     *
     * @param urlString A URL string, with any necessary url-encoding already applied @return The
     * <code>Rest</code> instance itself
     * @return This object, with urlString populated, ready for other builder-pattern config methods
     * or an HTTP verb method
     */
    public Rest url(final String urlString) {
        this.urlString = urlString;
        return this;
    }

    /**
     * <p>Sets a binary payload that will be sent as the request body for POST or PUT requests.
     * Any value set here will be ignored for GET requests.  Conversely, if a value IS set here...
     * then any additional parameter values set by <code>parameter()</code> will be ignored for POST
     * or PUT requests.</p>
     *
     * @param body The payload to send with a POST or PUT request (e.g. a JSON string)
     * @return This object, with body populated, ready for other builder-pattern config methods or
     * an HTTP verb method
     */
    public Rest body(final byte[] body) {
        this.body = body == null ? null : Arrays.copyOf(body, body.length);
        return this;
    }

    /**
     * <p>Adds a parameter to be sent with the HTTP request.  Depending on which HTTP verb is
     * ultimately used, this parameter may either be appended to the URL or else posted with the
     * request body.  Either way, both the parameter name and value will be automatically
     * url-encoded by the Rest client.</p>
     *
     * <p>For POST and PUT requests, these parameters will only be sent in the request body if that
     * body is otherwise unset.  In other words, if the <code>body()</code> method is invoked, then
     * <code>parameter()</code> invocations will be ignored for a POST or PUT.</p>
     *
     * <p>This method may be chained together repeatedly, to pass multiple parameters with a
     * request.  When the request is ultimately sent, the parameters will be sorted by their
     * names.</p>
     *
     * @param name The raw parameter name (not url-encoded)
     * @param value The raw parameter value (not url-encoded)
     * @return This object, with a parameter added, ready for other builder-pattern config methods
     * or an HTTP verb method
     * @throws RestException If any error occurs, or unexpected response received from Vault
     */
    @SuppressWarnings("CharsetObjectCanBeUsed") // Using Charset constant requires Java and above
    public Rest parameter(final String name, final String value) throws RestException {
        try {
            this.parameters.put(URLEncoder.encode(name, "UTF-8"),
                    URLEncoder.encode(value, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new RestException(e);
        }
        return this;
    }

    /**
     * <p>Adds the "X-Vault-Token" header to be sent with the HTTP request.</p>
     * *
     * <p>This method may be chained together repeatedly</p>
     *
     * @param value The vault token value as a {@code char[]}
     * @return This object, with a vault token added, ready for other builder-pattern config methods or
     * an HTTP verb method
     */
    public Rest token(final char[] value) {
        if (value != null && value.length > 0) {
            return header("X-Vault-Token", new String(value));
        }
        return this;
    }

    /**
     * <p>Adds a header to be sent with the HTTP request.</p>
     * *
     * <p>This method may be chained together repeatedly, to pass multiple headers with a request.
     * When the request is ultimately sent, the headers will be sorted by their names.</p>
     *
     * @param name The raw header name
     * @param value The raw header value
     * @return This object, with a header added, ready for other builder-pattern config methods or
     * an HTTP verb method
     */
    public Rest header(final String name, final String value) {
        if (value != null && !value.isEmpty()) {
            this.headers.put(name, value);
        }
        return this;
    }

    /**
     * <p>The number of seconds to wait before giving up on establishing an HTTP(S) connection.</p>
     *
     * @param connectTimeoutSeconds Number of seconds to wait for an HTTP(S) connection to
     * successfully establish
     * @return This object, with connectTimeoutSeconds populated, ready for other builder-pattern
     * config methods or an HTTP verb method
     */
    public Rest connectTimeoutSeconds(final Integer connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
        return this;
    }

    /**
     * <p>After an HTTP(S) connection has already been established, this is the number of seconds
     * to wait for all data to finish downloading.</p>
     *
     * @param readTimeoutSeconds Number of seconds to wait for all data to be retrieved from an
     * established HTTP(S) connection
     * @return This object, with readTimeoutSeconds populated, ready for other builder-pattern
     * config methods or an HTTP verb method
     */
    public Rest readTimeoutSeconds(final Integer readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
        return this;
    }

    /**
     * <p>Whether or not HTTPS connections should verify that the server has a valid SSL
     * certificate. Unless this is set to <code>false</code>, the default behavior is to always
     * verify SSL certificates.</p>
     *
     * <p>SSL CERTIFICATE VERIFICATION SHOULD NOT BE DISABLED IN PRODUCTION!  This feature is made
     * available to facilitate development or testing environments, where you might be using a
     * self-signed cert that will not pass verification.  However, even if you are using a
     * self-signed cert on your server, you can still leave SSL verification enabled and have your
     * application supply the cert using <code>pemFile()</code>, <code>pemResource()</code>, or
     * <code>pemUTF8()</code>.</p>
     *
     * @param sslVerification Whether or not to verify the SSL certificate used by the server with
     * HTTPS connections.  Default is <code>true</code>.
     * @return This object, with sslVerification populated, ready for other builder-pattern config
     * methods or an HTTP verb method
     */
    public Rest sslVerification(final Boolean sslVerification) {
        this.sslVerification = sslVerification;
        return this;
    }

    /**
     * <p>An {@link SSLContext}, as constructed by {@link SslConfig} within a
     * {@link VaultConfig} object.  Used when establishing an HTTPS connection, and provides access
     * to trusted server X509 certificates (as well as client certificates and private keys when TLS
     * client auth is used).</p>
     *
     * @param sslContext An SSLContext object, constructed by SslConfig
     * @return This object, with sslContext populated, ready for other builder-pattern config
     * methods or an HTTP verb method
     */
    public Rest sslContext(final SSLContext sslContext) {
        this.sslContext = sslContext;
        return this;
    }

    /**
     * <p>Executes an HTTP GET request with the settings already configured.  Parameters and
     * headers are optional, but a <code>RestException</code> will be thrown if the caller has not
     * first set a base URL with the <code>url()</code> method.</p>
     *
     * <p>If a body payload has been set through the <code>body()</code> method, then it will be
     * ignored when sending a GET request.</p>
     *
     * @return The result of the HTTP operation
     * @throws RestException If an error occurs, or an unexpected response received
     */
    public RestResponse get() throws RestException {
        try {
            var request = buildRequest(true);

            return send(request.GET().build());
        } catch (Exception e) {
            throw new RestException(e);
        }
    }

    /**
     * Executes an HTTP POST request with the settings already configured.  Parameters and headers
     * are optional, but a <code>RestException</code> will be thrown if the caller has not first set
     * a base URL with the <code>url()</code> method.
     *
     * <p>CGI parameters can always be passed via a query string on the URL.  Also, parameter
     * values set via the <code>parameter()</code> method will be sent with the POST request as form
     * data.  However, if a body payload is provided via the <code>body()</code> method, then that
     * takes precedence over any parameters set via <code>parameter()</code>, and those values will
     * be discarded.</p>
     *
     * @return The result of the HTTP operation
     * @throws RestException If an error occurs, or an unexpected response received
     */
    public RestResponse post() throws RestException {
        return postOrPutImpl(true);
    }

    /**
     * Executes an HTTP PUT request with the settings already configured.  Parameters and headers
     * are optional, but a <code>RestException</code> will be thrown if the caller has not first set
     * a base URL with the <code>url()</code> method.
     *
     * <p>CGI parameters can always be passed via a query string on the URL.  Also, parameter
     * values set via the <code>parameter()</code> method will be sent with the PUT request as form
     * data.  However, if a body payload is provided via the <code>body()</code> method, then that
     * takes precedence over any parameters set via <code>parameter()</code>, and those values will
     * be discarded.</p>
     *
     * @return The result of the HTTP operation
     * @throws RestException If an error occurs, or an unexpected response received
     */
    public RestResponse put() throws RestException {
        return postOrPutImpl(false);
    }

    /**
     * Executes an HTTP DELETE request with the settings already configured.  Parameters and headers
     * are optional, but a <code>RestException</code> will be thrown if the caller has not first set
     * a base URL with the <code>url()</code> method.
     *
     * <p> Note that any parameters are set in the query string.  This method does not send a
     * request body, as some HTTP servers will ignore it for DELETE requests.</p>
     *
     * @return The result of the HTTP operation
     * @throws RestException If an error occurs, or an unexpected response received
     */
    public RestResponse delete() throws RestException {
        try {
            var request = this.buildRequest(true);
            return send(request.DELETE().build());
        } catch (Exception e) {
            throw new RestException(e);
        }
    }


    /**
     * Since the implementations of a POST request and PUT request differ by only one line of code,
     * they are refactored into this private method which is turned wrapped by <code>post()</code>
     * and <code>put()</code>.
     *
     * @param doPost If <code>true</code>, then a POST operation will be performed.  If false, then
     * a PUT.
     * @return The result of the HTTP operation
     */
    private RestResponse postOrPutImpl(final boolean doPost) throws RestException {
        try {
            // Initialize HTTP(S) connection, and set any header values
            var request = this.buildRequest(false);
            request.header("Accept-Charset", "UTF-8");

            BodyPublisher payload;
            if (body != null) {
                payload = BodyPublishers.ofByteArray(body);
            } else if (!parameters.isEmpty()) {
                request.header("Content-Type",
                        "application/x-www-form-urlencoded;charset=UTF-8");
                payload = BodyPublishers.ofByteArray(
                        parametersToQueryString().getBytes(StandardCharsets.UTF_8));
            } else {
                payload = BodyPublishers.noBody();
            }

            if (doPost) {
                return send(request.POST(payload).build());
            } else {
                return send(request.PUT(payload).build());
            }

        } catch (IOException | InterruptedException | URISyntaxException e) {
            throw new RestException(e);
        }
    }

    /**
     * <p>This helper method constructs a query string (e.g. <code>param-1=foo&param-2=bar</code>)
     * from any parameters that have been set via the <code>param()</code> method.  Parameters will
     * be sorted by name.</p>
     *
     * @return A url-encoded URL query string
     */
    private String parametersToQueryString() {
        final var sj = new StringJoiner("&");
        parameters.forEach((name, value) -> sj.add(name + "=" + value));

        return sj.toString();
    }

    /**
     * This helper method initialize {@link HttpClient} and send {@link HttpRequest} to remote
     * resource
     *
     * @param req an {@link HttpRequest} request
     * @return A {@link RestResponse} instance
     * @throws IOException if connection fails
     * @throws InterruptedException if connection is interrupted
     */
    private RestResponse send(HttpRequest req) throws IOException, InterruptedException {
        final HttpClient client = getClient();

        var response = client.send(req, BodyHandlers.ofString());

        // Get the resulting status code
        final var statusCode = response.statusCode();

        // Download and parse response
        final var mimeType = response.headers().firstValue("Content-Type").orElse("");
        final var body = response.body().getBytes();

        return new RestResponse(statusCode, mimeType, body);
    }

    private HttpClient getClient() {
        if (configuredClient != null) {
            return configuredClient;
        }
        final var client = HttpClient.newBuilder()
                //Stick to HTTP/1.1 by default, coz Vault Agent fails proxying h2c request to https
                .version(Version.HTTP_1_1);

        if (connectTimeoutSeconds != null) {
            client.connectTimeout(Duration.of(connectTimeoutSeconds, ChronoUnit.SECONDS));
        }

        if (sslVerification != null && !sslVerification) {
            client.sslContext(DISABLED_SSL_CONTEXT);
        } else if (sslContext != null) {
            client.sslContext(sslContext);
        }
        return client.build();
    }


    /**
     * This helper method build an {@link HttpRequest.Builder} object used to send requests to
     * remote resource
     *
     * @param isGetOrDelete sets if request is called for a GET or DELETE request instead of POST or
     * PUT request
     * @return a {@link HttpRequest.Builder} bojnect
     * @throws URISyntaxException if passed URL isn't valid
     * @throws RestException if isn't passed an URL
     */
    private Builder buildRequest(Boolean isGetOrDelete) throws URISyntaxException, RestException {
        Optional.ofNullable(urlString).orElseThrow(() -> new RestException("No URL is set"));

        var uri = new URI(urlString);
        var params = isGetOrDelete ? parametersToQueryString() : "";
        var query = params;

        if (uri.getQuery() != null) {
            query = uri.getQuery();
            if (!params.isEmpty()) {
                query = uri.getQuery() + "&" + params;
            }
        }
        uri = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(),
                uri.getPath(), query, uri.getFragment());

        // Initialize HTTP(S) connection, and set any header values
        var request = HttpRequest.newBuilder()
                .uri(uri);

        headers.forEach(request::header);

        if (readTimeoutSeconds != null) {
            request.timeout(Duration.of(readTimeoutSeconds, ChronoUnit.SECONDS));
        }

        return request;
    }
}
