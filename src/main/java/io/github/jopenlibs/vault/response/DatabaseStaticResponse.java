package io.github.jopenlibs.vault.response;

import io.github.jopenlibs.vault.api.Logical;
import io.github.jopenlibs.vault.api.database.DatabaseStaticCredential;
import io.github.jopenlibs.vault.api.database.DatabaseStaticRoleOptions;
import io.github.jopenlibs.vault.json.JsonArray;
import io.github.jopenlibs.vault.json.JsonObject;
import io.github.jopenlibs.vault.json.JsonValue;
import io.github.jopenlibs.vault.rest.RestResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DatabaseStaticResponse extends LogicalResponse {

    private DatabaseStaticCredential credential;

    private DatabaseStaticRoleOptions roleOptions;

    public DatabaseStaticCredential getCredential() {
        return credential;
    }

    public DatabaseStaticRoleOptions getStaticRoleOptions() {
        return roleOptions;
    }

    /**
     * @param restResponse The raw HTTP response from Vault.
     * @param retries The number of retry attempts that occurred during the API call (can be zero).
     */
    public DatabaseStaticResponse(RestResponse restResponse, int retries) {
        super(restResponse, retries, Logical.logicalOperations.authentication);
        credential = buildCredentialFromData(this.getData());
        roleOptions = buildStaticRoleOptionsFromData(this.getData(), this.getDataObject());
    }

    private DatabaseStaticRoleOptions buildStaticRoleOptionsFromData(final Map<String, String> data,
            final JsonObject jsonObject) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        final List<String> rotationStatements = extractFromJsonArray(
                safeGetJsonArray(jsonObject, "rotation_statements"));

        final String credentialType = data.get("credential_type");
        final String dbName = data.get("db_name");
        final String lastVaultRotation = data.get("last_vault_rotation");
        final String rotationPeriod = data.get("rotation_period");
        final String skipImportRotationStr = data.get("skip_import_rotation");
        final Boolean skipImportRotation =
            skipImportRotationStr == null ? null : Boolean.parseBoolean(skipImportRotationStr);
        final String username = data.get("username");

        if (dbName == null && username == null) {
            return null;
        }

        return new DatabaseStaticRoleOptions()
                .rotationStatements(rotationStatements)
                .dbName(dbName)
                .lastVaultRotation(lastVaultRotation)
                .rotationPeriod(rotationPeriod)
                .skipImportRotation(skipImportRotation)
                .username(username);
    }

    private DatabaseStaticCredential buildCredentialFromData(final Map<String, String> data) {
        if (data == null) {
            return null;
        }
        final String username = data.get("username");
        final String password = data.get("password");
        final String lastRotation = data.get("last_vault_rotation");
        final String rotationPeriod = data.get("rotation_period");
        final String ttl = data.get("ttl");

        if (username == null && password == null) {
            return null;
        }

        return new DatabaseStaticCredential()
                .username(username)
                .password(password)
                .lastVaultRotation(lastRotation)
                .rotationPeriod(rotationPeriod)
                .ttl(ttl);
    }

    private JsonArray safeGetJsonArray(JsonObject source, String key) {
        if (source == null || source.get(key) == null || !source.get(key).isArray()) {
            return new JsonArray();
        }

        return source.get(key).asArray();
    }

    private List<String> extractFromJsonArray(JsonArray array) {
        return array.values().stream()
                .map(JsonValue::asString)
                .collect(Collectors.toUnmodifiableList());
    }
}
