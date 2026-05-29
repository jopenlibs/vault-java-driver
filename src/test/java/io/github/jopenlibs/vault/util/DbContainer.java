package io.github.jopenlibs.vault.util;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.lifecycle.TestDescription;
import org.testcontainers.lifecycle.TestLifecycleAware;

import static org.junit.Assume.assumeTrue;

public class DbContainer extends GenericContainer<DbContainer> implements TestConstants,
        TestLifecycleAware, TestRule {

    private static final Logger LOGGER = LoggerFactory.getLogger(DbContainer.class);

    public static final String hostname = "postgres";

    public DbContainer() {
        super("postgres:14-alpine");
        this.withNetwork(CONTAINER_NETWORK)
                .withNetworkAliases(hostname)
                .withEnv("POSTGRES_PASSWORD", POSTGRES_PASSWORD)
                .withEnv("POSTGRES_USER", POSTGRES_USER)
                .withExposedPorts(5432)
                .withLogConsumer(new Slf4jLogConsumer(LOGGER))
                .waitingFor(new HostPortWaitStrategy());
    }

    @Override
    public void beforeTest(TestDescription description) {
        assumeTrue(DOCKER_AVAILABLE);
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                start();
                try {
                    base.evaluate();
                } finally {
                    stop();
                }
            }
        };
    }
}
