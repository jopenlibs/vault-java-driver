package io.github.jopenlibs.vault.api;

import io.github.jopenlibs.vault.Vault;
import io.github.jopenlibs.vault.VaultException;
import io.github.jopenlibs.vault.api.database.DatabaseRoleOptions;
import io.github.jopenlibs.vault.api.database.DatabaseStaticRoleOptions;
import io.github.jopenlibs.vault.response.DatabaseResponse;
import io.github.jopenlibs.vault.response.DatabaseStaticResponse;
import io.github.jopenlibs.vault.util.DbContainer;
import io.github.jopenlibs.vault.util.TestConstants;
import io.github.jopenlibs.vault.util.VaultContainer;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import junit.framework.TestCase;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.testcontainers.containers.ExecConfig;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

public class AuthBackendDatabaseIT {

    @ClassRule
    public static final DbContainer dbContainer = new DbContainer();

    @ClassRule
    public static final VaultContainer container = new VaultContainer().dependsOn(dbContainer);


    @BeforeClass
    public static void setupClass() throws IOException, InterruptedException {
        container.initAndUnsealVault();
        container.setupBackendDatabase(DbContainer.hostname);
    }

    @Test
    public void testRoleCreation() throws VaultException {
        final Vault vault = container.getRootVault();

        final var creationStatements = List.of(
                "CREATE USER \"{{name}}\" WITH PASSWORD '{{password}}'; GRANT ALL PRIVILEGES ON DATABASE \"postgres\" to \"{{name}}\";");

        final var roleToCreate = new DatabaseRoleOptions().dbName("postgres")
                .creationStatements(creationStatements)
                .defaultTtl("100").maxTtl("300");

        DatabaseResponse response = vault.database().createOrUpdateRole("test-role", roleToCreate);
        TestCase.assertEquals(204, response.getRestResponse().getStatus());

        DatabaseResponse role = vault.database().getRole("test-role");
        TestCase.assertEquals(200, role.getRestResponse().getStatus());

        assertTrue(compareRoleOptions(role.getRoleOptions(), roleToCreate));
        TestCase.assertEquals("100", role.getRoleOptions().getDefaultTtl());
        TestCase.assertEquals("300", role.getRoleOptions().getMaxTtl());
    }

    @Test
    public void testDeleteRole() throws VaultException {
        final Vault vault = container.getRootVault();

        final var creationStatements = List.of(
                "CREATE USER \"{{name}}\" WITH PASSWORD '{{password}}'; GRANT ALL PRIVILEGES ON DATABASE \"postgres\" to \"{{name}}\";");

        final var roleToCreate = new DatabaseRoleOptions().dbName("postgres")
                .creationStatements(creationStatements);

        DatabaseResponse response = vault.database()
                .createOrUpdateRole("delete-role", roleToCreate);
        TestCase.assertEquals(204, response.getRestResponse().getStatus());

        DatabaseResponse deletedRole = vault.database().deleteRole("delete-role");
        TestCase.assertEquals(204, deletedRole.getRestResponse().getStatus());

        try {
            DatabaseResponse role = vault.database().getRole("delete-role");
        } catch (VaultException e) {
            assertEquals("This should have failed", 404, e.getHttpStatusCode());
        }
    }

    @Test
    public void testRoleNotFound() throws VaultException {
        final Vault vault = container.getRootVault();

        try {
            DatabaseResponse role = vault.database().getRole("i-do-not-exist");
        } catch (VaultException e) {
            assertEquals("This should have failed", 404, e.getHttpStatusCode());
        }
    }

    @Test
    public void testGetCredentials() throws VaultException {
        final Vault vault = container.getRootVault();

        final var creationStatements = List.of(
                "CREATE USER \"{{name}}\" WITH PASSWORD '{{password}}'; GRANT ALL PRIVILEGES ON DATABASE \"postgres\" to \"{{name}}\";");

        DatabaseResponse response = vault.database().createOrUpdateRole("new-role",
                new DatabaseRoleOptions().dbName("postgres")
                        .creationStatements(creationStatements));
        TestCase.assertEquals(204, response.getRestResponse().getStatus());

        DatabaseResponse credsResponse = vault.database().creds("new-role");
        TestCase.assertEquals(200, credsResponse.getRestResponse().getStatus());

        TestCase.assertTrue(credsResponse.getCredential().getUsername().contains("new-role"));
    }

    @Test
    public void testStaticRoleCreation() throws VaultException {
        final Vault vault = container.getRootVault();

        final var creationStatements = List.of(
                "CREATE USER \"{{name}}\" WITH PASSWORD '{{password}}'; GRANT ALL PRIVILEGES ON DATABASE \"postgres\" to \"{{name}}\";");

        DatabaseResponse response = vault.database().createOrUpdateRole("base-role",
                new DatabaseRoleOptions().dbName("postgres")
                        .creationStatements(creationStatements));
        TestCase.assertEquals(204, response.getRestResponse().getStatus());

        DatabaseResponse credsResponse = vault.database().creds("base-role");

        final var rotationStatements = List.of(
                "ALTER USER \"{{name}}\" WITH PASSWORD '{{password}}';");

        final var roleToCreate =
            new DatabaseStaticRoleOptions().dbName("postgres")
                .username(credsResponse.getCredential().getUsername())
                .skipImportRotation(false)
                .rotationStatements(rotationStatements)
                .rotationPeriod("300").credentialType("password");

        DatabaseStaticResponse staticResponse =
            vault.database().createOrUpdateStaticRole("test-static-role", roleToCreate);
        TestCase.assertEquals(204, staticResponse.getRestResponse().getStatus());

        DatabaseStaticResponse role =
            vault.database().getStaticRole("test-static-role");
        assertEquals("300", role.getStaticRoleOptions().getRotationPeriod());
        assertEquals(Boolean.FALSE, role.getStaticRoleOptions().getSkipImportRotation());
        assertEquals(credsResponse.getCredential().getUsername(),
                     role.getStaticRoleOptions().getUsername());

        DatabaseStaticResponse rotCredsResponse =
            vault.database().staticCreds("test-static-role");
        assertEquals(credsResponse.getCredential().getUsername(),
                     rotCredsResponse.getCredential().getUsername());
        assertNotNull(rotCredsResponse.getCredential().getPassword());
        assertTrue(!credsResponse.getCredential().getPassword()
                   .equals(rotCredsResponse.getCredential().getPassword()));
        assertEquals(roleToCreate.getRotationPeriod(),
                     rotCredsResponse.getCredential().getRotationPeriod());
        assertNotNull(rotCredsResponse.getCredential().getLastVaultRotation());
    }

    @Test
    public void testDeleteStaticRole() throws Exception {
        final Vault vault = container.getRootVault();

        String[] psql = new String[] {
            "psql", "-U", TestConstants.POSTGRES_USER, "-w", "-c",
            "create user \"del-static-role\" with password 'password'" };
        DbContainer.ExecResult result =
            dbContainer.execInContainer(ExecConfig.builder()
                                        .command(psql)
                                        .envVars(Map.of("PGPASSWORD", TestConstants.POSTGRES_PASSWORD))
                                        .build());
        assertEquals(0, result.getExitCode());
        assertEquals("CREATE ROLE", result.getStdout().trim());

        final var rotationStatements = List.of(
                "ALTER USER \"{{name}}\" WITH PASSWORD '{{password}}';");

        final var roleToCreate =
            new DatabaseStaticRoleOptions().dbName("postgres")
                .username("del-static-role")
                .skipImportRotation(true)
                .rotationPeriod("300")
                .rotationStatements(rotationStatements);

        DatabaseStaticResponse staticResponse =
            vault.database().createOrUpdateStaticRole("del-static-role", roleToCreate);
        TestCase.assertEquals(204, staticResponse.getRestResponse().getStatus());
        DatabaseStaticResponse checkRole =
            vault.database().getStaticRole("del-static-role");
        // Vault doesn't know the password yet, so this is null
        // This verifies skipImportRotation=true is working
        assertTrue(checkRole.getCredential().getPassword() == null);

        DatabaseStaticResponse deletedRole =
            vault.database().deleteStaticRole("del-static-role");
        TestCase.assertEquals(204, deletedRole.getRestResponse().getStatus());

        try {
            DatabaseStaticResponse role = vault.database().getStaticRole("delete-role");
        } catch (VaultException e) {
            assertEquals("This should have failed", 404, e.getHttpStatusCode());
        }
    }

    private boolean compareRoleOptions(DatabaseRoleOptions expected, DatabaseRoleOptions actual) {
        return expected.getCreationStatements().size() == actual.getCreationStatements().size() &&
                expected.getRenewStatements().size() == actual.getRenewStatements().size() &&
                expected.getRevocationStatements().size() == actual.getRevocationStatements().size()
                &&
                expected.getRollbackStatements().size() == actual.getRollbackStatements().size();
    }
}
