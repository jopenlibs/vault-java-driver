package io.github.jopenlibs.vault.api.database;

public class DatabaseStaticCredential extends DatabaseCredential {
    private String lastVaultRotation;
    private String ttl;
    private String rotationPeriod;

    /**
     * @return Returns the last vault rotation time associated with the retrieved Credential
     */
    public String getLastVaultRotation() {
        return lastVaultRotation;
    }

    /**
     * @return Returns the TTL associated with the retrieved Credential
     */
    public String getTtl() {
        return ttl;
    }

    /**
     * @return Returns the rotation period associated with the retrieved Credential
     */
    public String getRotationPeriod() {
        return rotationPeriod;
    }

    public DatabaseStaticCredential username(String username) {
        super.username(username);
        return this;
    }

    public DatabaseStaticCredential password(String password) {
        super.password(password);
        return this;
    }

    public DatabaseStaticCredential rotationPeriod(String period) {
        this.rotationPeriod = period;
        return this;
    }

    public DatabaseStaticCredential ttl(String ttl) {
        this.ttl = ttl;
        return this;
    }

    public DatabaseStaticCredential lastVaultRotation(String rotation) {
        this.lastVaultRotation = rotation;
        return this;
    }
}
