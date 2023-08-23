package io.github.jopenlibs.vault.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Additional options that may be set as part of K/V V2 write operation.
 * Construct instances of this class using a builder pattern, calling setter methods for each
 * value and then terminating with a call to build().
 */
public class WriteOptions {

    public static final String CHECK_AND_SET_KEY = "cas";

    private final Map<String, Object> options = new HashMap<>();

    /**
     * Enable check and set (CAS) option
     * @param version current version of the secret
     * @return updated options ready for additional builder-pattern calls or else finalization
     * with the build() method
     */
    public WriteOptions checkAndSet(Long version) {
        return setOption(CHECK_AND_SET_KEY, version);
    }

    /**
     * Set an option to a value
     * @param name option name
     * @param value option value
     * @return updated options ready for additional builder-pattern calls or else finalization
     * with the build() method
     */
    public WriteOptions setOption(String name, Object value) {
        options.put(name, value);
        return this;
    }

    /**
     * Finalize the options (terminating method in the builder pattern)
     * @return this object, with all available config options parsed and loaded
     */
    public WriteOptions build() {
        return this;
    }

    /**
     * @return options as a Map
     */
    public Map<String, Object> getOptionsMap() {
        return Collections.unmodifiableMap(options);
    }

    /**
     * @return true if no options are set, false otherwise
     */
    public boolean isEmpty() {
        return options.isEmpty();
    }

}
