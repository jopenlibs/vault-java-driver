package io.github.jopenlibs.vault.api;

public class Utils {
    /**
     * Prevent creation an instance of a utility class.
     */
    private Utils() {
        // No-op.
    }

    /**
     * Utility thread sleep.
     *
     * @param duration sleep duration in milliseconds.
     */
    public static void sleep(final int duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e1) {
            e1.printStackTrace();
        }
    }
}
