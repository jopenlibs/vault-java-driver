package io.github.jopenlibs.vault.response;

import io.github.jopenlibs.vault.api.Logical;
import io.github.jopenlibs.vault.api.Logical.logicalOperations;
import io.github.jopenlibs.vault.json.Json;
import io.github.jopenlibs.vault.json.JsonObject;
import io.github.jopenlibs.vault.json.JsonValue;
import io.github.jopenlibs.vault.rest.RestResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class is a container for the information returned by Vault in logical API operations (e.g.
 * read, write).
 */
public class LogicalResponse extends VaultResponse {

    private final Map<String, String> data = new HashMap<>();
    private final List<String> listData = new ArrayList<>();
    private final List<String> listSubkeys = new ArrayList<>();
    private final Map<String, String> dataMetadata = new HashMap<>();
    private JsonObject dataObject = null;
    private String leaseId;
    private WrapResponse wrapResponse;
    private Boolean renewable;
    private Long leaseDuration;

    /**
     * @param restResponse The raw HTTP response from Vault.
     * @param retries The number of retry attempts that occurred during the API call (can be zero).
     * @param operation The operation requested.
     */
    public LogicalResponse(final RestResponse restResponse, final int retries,
            final Logical.logicalOperations operation) {
        super(restResponse, retries);
        parseMetadataFields();
        parseResponseData(operation);
    }

    public Map<String, String> getData() {
        return data;
    }

    public List<String> getListData() {
        return listData;
    }

    public JsonObject getDataObject() {
        return dataObject;
    }

    public String getLeaseId() {
        return leaseId;
    }

    public Boolean getRenewable() {
        return renewable;
    }

    public Long getLeaseDuration() {
        return leaseDuration;
    }

    public WrapResponse getWrapResponse() {
        return wrapResponse;
    }

    public DataMetadata getDataMetadata() {
        return new DataMetadata(dataMetadata);
    }

    public List<String> getListSubkeys() {
        return listSubkeys;
    }

    private void parseMetadataFields() {
        try {
            final var jsonString = new String(getRestResponse().getBody(),
                    StandardCharsets.UTF_8);
            final var jsonObject = Json.parse(jsonString).asObject();

            this.leaseId = jsonObject.get("lease_id").asString();
            this.renewable = jsonObject.get("renewable").asBoolean();
            this.leaseDuration = jsonObject.get("lease_duration").asLong();

            this.wrapResponse = new WrapResponse(getRestResponse(), getRetries());
        } catch (Exception ignored) {
        }
    }

    private void parseResponseData(final Logical.logicalOperations operation) {
        try {
            final var jsonString = new String(getRestResponse().getBody(),
                    StandardCharsets.UTF_8);
            var jsonObject = Json.parse(jsonString).asObject();
            if (operation.equals(Logical.logicalOperations.readV2)) {
                jsonObject = jsonObject.get("data").asObject();
                final var metadataValue = jsonObject.get("metadata");
                if (null != metadataValue) {
                    parseJsonIntoMap(metadataValue.asObject(), dataMetadata);
                }
            }

            dataObject = jsonObject.get("data").asObject();
            parseJsonIntoMap(dataObject, data);

            // For list operations convert the array of keys to a list of values
            if (operation.equals(Logical.logicalOperations.listV1) || operation.equals(
                    Logical.logicalOperations.listV2)) {
                if (getRestResponse().getStatus() != 404 && data.get("keys") != null) {

                    final var keys = Json.parse(data.get("keys")).asArray();
                    keys.forEach(key -> listData.add(key.asString()));
                }

            }

            if (operation.equals(logicalOperations.listSubKeys)) {
                if (data.containsKey("subkeys")) {
                    final var keys = Json.parse(data.get("subkeys")).asObject();
                    this.listSubkeys.addAll(keys.names());
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void parseJsonIntoMap(final JsonObject jsonObject, final Map<String, String> map) {
        for (final JsonObject.Member member : jsonObject) {
            final JsonValue jsonValue = member.getValue();
            if (jsonValue == null || jsonValue.isNull()) {
                continue;
            } else if (jsonValue.isString()) {
                map.put(member.getName(), jsonValue.asString());
            } else {
                map.put(member.getName(), jsonValue.toString());
            }
        }
    }

}
