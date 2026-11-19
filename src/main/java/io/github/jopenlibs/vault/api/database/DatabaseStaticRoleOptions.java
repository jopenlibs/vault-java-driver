package io.github.jopenlibs.vault.api.database;

import java.util.ArrayList;
import java.util.List;

public class DatabaseStaticRoleOptions {

    private String name;
    private String dbName;
    private List<String> rotationStatements = new ArrayList<>();
    private String username;
    private String credentialType;
    private String lastVaultRotation;
    private String rotationPeriod;
    private Boolean skipImportRotation;

    public String getName() {
        return name;
    }

    public String getDbName() {
        return dbName;
    }

    public String getCredentialType() {
        return credentialType;
    }

    public String getLastVaultRotation() {
        return lastVaultRotation;
    }

    public String getRotationPeriod() {
        return rotationPeriod;
    }

    public Boolean getSkipImportRotation() {
        return skipImportRotation;
    }

    public String getUsername() {
        return username;
    }

    public List<String> getRotationStatements() {
        return rotationStatements;
    }

    /**
     * @param name {@code String} – Specifies the name of the role to create. This is specified as
     * part of the URL.
     * @return This object, with name populated, ready for other builder methods or immediate use.
     */
    public DatabaseStaticRoleOptions name(final String name) {
        this.name = name;
        return this;
    }

    /**
     * @param dbName {@code String} - The name of the database connection to use for this role.
     * @return This object, with dbName populated, ready for other builder methods or immediate use.
     */
    public DatabaseStaticRoleOptions dbName(final String dbName) {
        this.dbName = dbName;
        return this;
    }

    /**
     * @param
     * @return This object, with username populated, ready for other builder methods or immediate
     * use.
     */
    public DatabaseStaticRoleOptions username(final String username) {
        this.username = username;
        return this;
    }

    /**
     * @param
     * @return This object, with skipImportRotation populated, ready for other builder methods or immediate
     * use.
     */
    public DatabaseStaticRoleOptions skipImportRotation(final Boolean skipImportRotation) {
        this.skipImportRotation = skipImportRotation;
        return this;
    }

    /**
     * @param
     * @return This object, with rotationPeriod populated, ready for other builder methods or immediate
     * use.
     */
    public DatabaseStaticRoleOptions rotationPeriod(final String rotationPeriod) {
        this.rotationPeriod = rotationPeriod;
        return this;
    }

    /**
     * @param
     * @return This object, with lastVaultRotation populated, ready for other builder methods or immediate
     * use.
     */
    public DatabaseStaticRoleOptions lastVaultRotation(final String lastVaultRotation) {
        this.lastVaultRotation = lastVaultRotation;
        return this;
    }

    /**
     * @param
     * @return This object, with credentialType populated, ready for other builder methods or immediate
     * use.
     */
    public DatabaseStaticRoleOptions credentialType(final String credentialType) {
        this.credentialType = credentialType;
        return this;
    }

    /**
     * @param creationStatements {@code List<String>} – Specifies the database statements executed
     * to rotate the password for a user. See the plugin's API page for more information on support and
     * formatting for this parameter.
     * @return This object, with rotationStatements populated, ready for other builder methods or
     * immediate use.
     */
    public DatabaseStaticRoleOptions rotationStatements(final List<String> rotationStatements) {
        this.rotationStatements = rotationStatements;
        return this;
    }
}
