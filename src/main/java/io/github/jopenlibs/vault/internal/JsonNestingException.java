package io.github.jopenlibs.vault.internal;

/**
 * Thrown by {@link SafeJson#parse(String)} when JSON is nested deeper than
 * {@link SafeJson#MAX_NESTING_DEPTH}.
 */
public class JsonNestingException extends RuntimeException {

    private static final long serialVersionUID = 6370532845419260815L;

    public JsonNestingException(final String message) {
        super(message);
    }
}
