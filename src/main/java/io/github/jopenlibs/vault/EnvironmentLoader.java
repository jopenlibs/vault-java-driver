package io.github.jopenlibs.vault;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The code used to load environment variables is encapsulated within an inner class, so that a mock
 * version of that environment loader can be used by unit tests.
 */
public class EnvironmentLoader implements Serializable {

    // The value computed for release 6.2.3, which declared none: keeps its serialized configs readable
    private static final long serialVersionUID = 1109558394682383170L;

    /**
     * Loads an environment variable. Surrounding whitespace (e.g. a trailing newline) is removed,
     * and an empty or blank value is treated as unset.
     *
     * <p>If <code>VAULT_TOKEN</code> is unset, it is read from the <code>.vault-token</code> file
     * in the executing user's home directory, if any.</p>
     *
     * @param name The environment variable name
     * @return The variable value, or <code>null</code> if it is unset
     */
    public String loadVariable(final String name) {
        String value = normalize(getenv(name));
        if (value == null && VaultConfig.VAULT_TOKEN.equals(name)) {
            // Not in the environment, looking for a ".vault-token" file in the executing user's home directory instead
            try {
                value = normalize(Files.readString(userHome().resolve(".vault-token")));
            } catch (IOException e) {
                // No-op... there simply isn't a token value available
            }
        }
        return value;
    }

    /**
     * Loads an environment variable with the given loader, removing surrounding whitespace and
     * treating an empty or blank value as unset. This is done here too, because
     * {@link #loadVariable(String)} may be overridden.
     *
     * @param loader The environment loader to use
     * @param name The environment variable name
     * @return The variable value without surrounding whitespace, or <code>null</code> if it is
     * unset, empty or blank
     */
    static String load(final EnvironmentLoader loader, final String name) {
        return normalize(loader.loadVariable(name));
    }

    /**
     * Reads a variable from the process environment. Overridden by unit tests.
     */
    String getenv(final String name) {
        return System.getenv(name);
    }

    /**
     * @return The executing user's home directory. Overridden by unit tests.
     */
    Path userHome() {
        return Paths.get(System.getProperty("user.home"));
    }

    private static String normalize(final String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

}
