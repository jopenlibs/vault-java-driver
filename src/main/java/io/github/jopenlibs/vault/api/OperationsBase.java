package io.github.jopenlibs.vault.api;

import io.github.jopenlibs.vault.RetryPolicy;
import io.github.jopenlibs.vault.VaultConfig;
import io.github.jopenlibs.vault.VaultException;
import io.github.jopenlibs.vault.rest.Rest;
import io.github.jopenlibs.vault.rest.RestException;
import java.io.IOException;


/**
 * The base class for all operation.
 */
public abstract class OperationsBase {

    protected final VaultConfig config;

    /**
     * The retries for this instance, or <code>null</code> to use the retries currently set in
     * {@link #config}.
     */
    protected final RetryPolicy retryPolicy;

    protected OperationsBase(VaultConfig config) {
        this(config, null);
    }

    /**
     * @param config The configuration
     * @param retryPolicy The retries for this instance, overriding those set in the configuration,
     * or <code>null</code> to use the retries currently set in the configuration, read on every
     * call
     */
    protected OperationsBase(VaultConfig config, RetryPolicy retryPolicy) {
        this.config = config;
        this.retryPolicy = retryPolicy;
    }

    /**
     * @return The maximum number of retries from the retry policy if set, otherwise the one
     * currently set in the configuration
     */
    protected int getMaxRetries() {
        return retryPolicy != null ? retryPolicy.getMaxRetries() : config.getMaxRetries();
    }

    /**
     * @return The interval between retries from the retry policy if set, otherwise the one
     * currently set in the configuration
     */
    protected int getRetryIntervalMilliseconds() {
        return retryPolicy != null
                ? retryPolicy.getRetryIntervalMilliseconds()
                : config.getRetryIntervalMilliseconds();
    }

    protected <T> T retry(final EndpointOperation<T> op) throws VaultException {
        return retry(op, getMaxRetries(), getRetryIntervalMilliseconds());
    }

    /**
     * Runs an operation, retrying it up to <code>retryCount</code> times when it fails with a
     * transient error (see {@link #isRetryable(Exception)}). Any other error, or an interrupt
     * while waiting between attempts, ends the retries and the last error is thrown.
     */
    static <T> T retry(final EndpointOperation<T> op, int retryCount, long retryIntervalMs)
            throws VaultException {
        int attempt = 0;

        while (true) {
            try {
                return op.run(attempt);
            } catch (final Exception e) {
                // If there are retries to perform, then pause for the configured interval and then execute the loop again...
                if (attempt < retryCount && isRetryable(e) && sleep(retryIntervalMs)) {
                    attempt++;
                } else if (e instanceof VaultException) {
                    // ... otherwise, give up.
                    throw (VaultException) e;
                } else {
                    throw new VaultException(e);
                }
            }
        }
    }

    protected Rest getRest() {
        return new Rest(config.getHttpClient());
    }

    public interface EndpointOperation<T> {

        /**
         * Run an operation.
         *
         * @param attempt Number of current attempt.
         * @return Operation response.
         * @throws Exception When an error occurs during operation execution.
         */
        T run(int attempt) throws Exception;
    }

    /**
     * Tells whether a failed operation may succeed if run again: I/O errors (e.g. connection
     * refused, timeout), and HTTP 5xx, 408, 412 and 429 responses. Other 4xx responses, invalid
     * arguments and unexpected payloads fail the same way on every attempt.
     */
    static boolean isRetryable(final Exception e) {
        if (e instanceof VaultException) {
            final int status = ((VaultException) e).getHttpStatusCode();
            return status >= 500 || status == 408 || status == 412 || status == 429;
        }
        if (e instanceof RestException) {
            return e.getCause() instanceof IOException;
        }
        return e instanceof IOException;
    }

    /**
     * @return <code>false</code> if the thread was interrupted while sleeping, in which case the
     * interrupt flag is restored
     */
    private static boolean sleep(long delay) {
        try {
            Thread.sleep(delay);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

}
