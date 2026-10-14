package io.github.jopenlibs.vault.response;

import io.github.jopenlibs.vault.rest.RestResponse;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Unit tests verifying that response classes tolerate missing and <code>null</code> JSON fields.
 */
public class ResponseNullHandlingTests {

    private static RestResponse json(final String body) {
        return new RestResponse(200, "application/json", body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void testAuthResponseWithoutPoliciesOrRenewable() {
        final var response = new AuthResponse(
                json("{\"auth\":{\"client_token\":\"token\",\"metadata\":null}}"), 0);
        assertEquals("token", response.getAuthClientToken());
        assertNull(response.getAuthPolicies());
        assertNull(response.getRenewable());
    }

    @Test
    public void testAuthResponseWithNullFields() {
        final var response = new AuthResponse(
                json("{\"auth\":{\"policies\":null},\"renewable\":null}"), 0);
        assertNull(response.getAuthPolicies());
        assertNull(response.getRenewable());
    }

    @Test
    public void testWrapResponseWithoutToken() {
        final var response = new WrapResponse(json("{\"wrap_info\":{\"accessor\":\"acc\"}}"), 0);
        assertNull(response.getToken());
        assertEquals("acc", response.getAccessor());
        assertNull(response.getRenewable());
    }

    @Test
    public void testLookupResponseWithNullFields() {
        final var response = new LookupResponse(
                json("{\"data\":{\"id\":\"id\",\"last_renewal_time\":null,\"metadata\":null}}"), 0);
        assertEquals("id", response.getId());
        assertNull(response.getLastRenewalTime());
        assertNull(response.getPolicies());
    }

    @Test
    public void testLookupResponseWithoutData() {
        final var response = new LookupResponse(json("{\"errors\":[]}"), 0);
        assertNull(response.getId());
    }

    @Test
    public void testUnwrapResponseWithoutData() {
        final var response = new UnwrapResponse(json("{\"auth\":{\"client_token\":\"t\"}}"), 0);
        assertNull(response.getData());
    }
}
