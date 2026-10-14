package io.github.jopenlibs.vault;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests verifying that SSL settings survive Java serialization.
 */
public class SslConfigSerializationTests {

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(final T object) throws IOException, ClassNotFoundException {
        final var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(object);
        }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) in.readObject();
        }
    }

    private static String certPem() throws IOException {
        try (var input = SslConfigSerializationTests.class.getResourceAsStream("/cert.pem")) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void testVerifyEnabledSurvives() throws Exception {
        assertTrue(roundTrip(new SslConfig().verify(true).build()).isVerify());
    }

    @Test
    public void testVerifyDisabledSurvives() throws Exception {
        assertFalse(roundTrip(new SslConfig().verify(false).build()).isVerify());
    }

    /**
     * A stream from an older release, or from a never-built instance, carries <code>verify</code>
     * set to <code>false</code> without it being requested: verification must stay enabled.
     */
    @Test
    public void testUnbuiltVerifyFalseIsNotRestored() throws Exception {
        final var config = new SslConfig();
        final Field verify = SslConfig.class.getDeclaredField("verify");
        verify.setAccessible(true);
        verify.setBoolean(config, false);

        assertTrue(roundTrip(config).isVerify());
    }

    @Test
    public void testVerifyDisabledByEnvironmentSurvives() throws Exception {
        final var config = new SslConfig().environmentLoader(new VerifyFalseLoader()).build();

        assertFalse(config.isVerify());
        assertFalse(roundTrip(config).isVerify());
    }

    private static class VerifyFalseLoader extends EnvironmentLoader {

        @Override
        public String loadVariable(final String name) {
            return "VAULT_SSL_VERIFY".equals(name) ? "false" : null;
        }
    }

    /**
     * Reads an <code>SslConfig</code> serialized by release 6.2.3.
     */
    private static SslConfig read623(final String name) throws Exception {
        try (var in = new ObjectInputStream(SslConfigSerializationTests.class
                .getResourceAsStream("/serialized-6.2.3/" + name))) {
            return (SslConfig) in.readObject();
        }
    }

    @Test
    public void testRelease623UnbuiltConfigVerifies() throws Exception {
        assertTrue(read623("sslconfig-unbuilt.ser").isVerify());
    }

    @Test
    public void testRelease623BuiltConfigVerifies() throws Exception {
        assertTrue(read623("sslconfig-built.ser").isVerify());
    }

    @Test
    public void testRelease623VerifyDisabledSurvives() throws Exception {
        assertFalse(read623("sslconfig-verify-false.ser").isVerify());
    }

    @Test
    public void testPemCertificateSurvives() throws Exception {
        final String pem = certPem();
        final var copy = roundTrip(new SslConfig().pemUTF8(pem).build());

        assertEquals(pem, copy.getPemUTF8());
        assertNotNull(copy.getSslContext());
    }

    /**
     * A deserialized, already built <code>SslConfig</code> is not rebuilt by
     * <code>VaultConfig.build()</code>.
     */
    @Test
    public void testBuiltStateSurvives() throws Exception {
        final var copy = roundTrip(new SslConfig().pemUTF8(certPem()).build());
        final var sslContext = copy.getSslContext();

        new VaultConfig().address("https://127.0.0.1:8200").sslConfig(copy).build();
        assertSame(sslContext, copy.getSslContext());
    }

    @Test
    public void testVaultConfigSslSettingsSurvive() throws Exception {
        final var config = new VaultConfig()
                .address("https://127.0.0.1:8200")
                .sslConfig(new SslConfig().pemUTF8(certPem()))
                .build();
        final var copy = roundTrip(config);

        assertTrue(copy.getSslConfig().isVerify());
        assertNotNull(copy.getSslConfig().getSslContext());
    }
}
