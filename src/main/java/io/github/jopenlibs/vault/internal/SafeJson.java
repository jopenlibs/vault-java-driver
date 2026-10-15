package io.github.jopenlibs.vault.internal;

import io.github.jopenlibs.vault.json.Json;
import io.github.jopenlibs.vault.json.JsonValue;

/**
 * Parses JSON received from Vault, rejecting documents nested too deeply before they reach the
 * vendored parser.
 *
 * <p>The vendored parser descends into nested arrays and objects by recursion. Its own nesting
 * limit (1000 levels) needs more stack than some threads have, so a deeply nested value stored in
 * Vault could still raise {@link StackOverflowError} in the reading thread. This class checks the
 * nesting depth first, with a loop that uses no stack per level, and throws
 * {@link JsonNestingException} above {@link #MAX_NESTING_DEPTH}.</p>
 *
 * <p>This class is internal to the driver: its package is not exported by the module.</p>
 */
public final class SafeJson {

    /**
     * Maximum depth of nested arrays and objects accepted. Vault 1.20 and later reject JSON nested
     * deeper than 298 levels, so valid Vault responses stay well below this limit.
     */
    public static final int MAX_NESTING_DEPTH = 500;

    private SafeJson() {
    }

    /**
     * Parses the given string as JSON, after checking its nesting depth.
     *
     * @param string the input string, must be valid JSON
     * @return a value that represents the parsed JSON
     * @throws JsonNestingException if arrays and objects are nested deeper than
     * {@link #MAX_NESTING_DEPTH}
     * @throws io.github.jopenlibs.vault.json.ParseException if the input is not valid JSON
     */
    public static JsonValue parse(final String string) {
        checkNestingDepth(string, MAX_NESTING_DEPTH);
        return Json.parse(string);
    }

    /**
     * Checks that arrays and objects in the given JSON text are not nested deeper than the given
     * limit. Brackets inside strings are ignored. Other syntax errors are left to the parser.
     *
     * @param string the JSON text, ignored if <code>null</code>
     * @param maxDepth the maximum nesting depth accepted
     * @throws JsonNestingException if the limit is exceeded
     */
    static void checkNestingDepth(final String string, final int maxDepth) {
        if (string == null) {
            return;
        }

        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < string.length(); i++) {
            final char c = string.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '[' || c == '{') {
                if (++depth > maxDepth) {
                    throw new JsonNestingException(String.format(
                            "JSON nested deeper than %d levels at offset %d", maxDepth, i));
                }
            } else if ((c == ']' || c == '}') && depth > 0) {
                depth--;
            }
        }
    }
}
