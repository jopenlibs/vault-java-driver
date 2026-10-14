package io.github.jopenlibs.vault;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for the default <code>EnvironmentLoader</code>, with a fake process environment and
 * home directory.
 */
public class EnvironmentLoaderTests {

    @Rule
    public TemporaryFolder home = new TemporaryFolder();

    private final Map<String, String> env = new HashMap<>();

    private final EnvironmentLoader loader = new EnvironmentLoader() {
        @Override
        String getenv(final String name) {
            return env.get(name);
        }

        @Override
        Path userHome() {
            return home.getRoot().toPath();
        }
    };

    private void writeTokenFile(final String content) throws IOException {
        Files.writeString(home.getRoot().toPath().resolve(".vault-token"), content);
    }

    @Test
    public void testValueIsReturned() {
        env.put("VAULT_ADDR", "http://127.0.0.1:8200");
        assertEquals("http://127.0.0.1:8200", loader.loadVariable("VAULT_ADDR"));
    }

    @Test
    public void testValueIsTrimmed() {
        env.put("VAULT_ADDR", " http://127.0.0.1:8200\n");
        assertEquals("http://127.0.0.1:8200", loader.loadVariable("VAULT_ADDR"));
        env.put("VAULT_TOKEN", "from-env\r\n");
        assertEquals("from-env", loader.loadVariable("VAULT_TOKEN"));
    }

    @Test
    public void testUnsetEmptyAndBlankValuesAreNull() {
        assertNull(loader.loadVariable("VAULT_ADDR"));
        env.put("VAULT_ADDR", "");
        assertNull(loader.loadVariable("VAULT_ADDR"));
        env.put("VAULT_ADDR", "  \n");
        assertNull(loader.loadVariable("VAULT_ADDR"));
    }

    @Test
    public void testTokenFromEnvironmentWinsOverFile() throws IOException {
        writeTokenFile("from-file");
        env.put("VAULT_TOKEN", "from-env");
        assertEquals("from-env", loader.loadVariable("VAULT_TOKEN"));
    }

    @Test
    public void testEmptyTokenFallsBackToFile() throws IOException {
        writeTokenFile("from-file\n");
        env.put("VAULT_TOKEN", "");
        assertEquals("from-file", loader.loadVariable("VAULT_TOKEN"));
    }

    @Test
    public void testBlankTokenFileIsNull() throws IOException {
        writeTokenFile("  \n");
        assertNull(loader.loadVariable("VAULT_TOKEN"));
    }

    @Test
    public void testMissingTokenFileIsNull() {
        assertNull(loader.loadVariable("VAULT_TOKEN"));
    }
}
