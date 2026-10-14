package io.github.jopenlibs.vault.response;

import io.github.jopenlibs.vault.json.JsonObject;
import io.github.jopenlibs.vault.rest.RestResponse;

/**
 * This class is a container for the information returned by Vault in unwrap backend operations.
 */
public class UnwrapResponse extends AuthResponse {

    /**
     * This constructor simply exposes the common base class constructor.
     *
     * @param restResponse The raw HTTP response from Vault.
     * @param retries The number of retry attempts that occurred during the API call (can be zero).
     */
    public UnwrapResponse(final RestResponse restResponse, final int retries) {
        super(restResponse, retries);
    }

    /**
     * @return The <code>data</code> object of the unwrapped response, or <code>null</code> if it
     * has none
     */
    public JsonObject getData() {
        if (jsonResponse == null) {
            return null;
        }
        final var data = jsonResponse.get("data");
        return data != null && data.isObject() ? data.asObject() : null;
    }
}
