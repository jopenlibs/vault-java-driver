package io.github.jopenlibs.vault;

/**
 * <p>An immutable retry setting for API operations: the maximum number of times a failing
 * operation is retried, and the time to wait between attempts.</p>
 *
 * <p>A policy with <code>maxRetries</code> set to <code>0</code> disables retries.</p>
 *
 * @see Vault#retrying(int, int)
 * @see VaultConfig#retries(int, int)
 */
public final class RetryPolicy {

    private final int maxRetries;
    private final int retryIntervalMilliseconds;

    /**
     * Creates a retry policy.
     *
     * @param maxRetries The number of times that a failing API operation will be retried
     * @param retryIntervalMilliseconds The number of milliseconds to wait between retries
     * @throws IllegalArgumentException If either value is negative
     */
    public RetryPolicy(final int maxRetries, final int retryIntervalMilliseconds) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must not be negative: " + maxRetries);
        }
        if (retryIntervalMilliseconds < 0) {
            throw new IllegalArgumentException(
                    "retryIntervalMilliseconds must not be negative: " + retryIntervalMilliseconds);
        }
        this.maxRetries = maxRetries;
        this.retryIntervalMilliseconds = retryIntervalMilliseconds;
    }

    /**
     * @return The number of times that a failing API operation will be retried
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * @return The number of milliseconds to wait between retries
     */
    public int getRetryIntervalMilliseconds() {
        return retryIntervalMilliseconds;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RetryPolicy)) {
            return false;
        }
        final RetryPolicy other = (RetryPolicy) o;
        return maxRetries == other.maxRetries
                && retryIntervalMilliseconds == other.retryIntervalMilliseconds;
    }

    @Override
    public int hashCode() {
        return 31 * maxRetries + retryIntervalMilliseconds;
    }

    @Override
    public String toString() {
        return "RetryPolicy{maxRetries=" + maxRetries
                + ", retryIntervalMilliseconds=" + retryIntervalMilliseconds + "}";
    }
}
