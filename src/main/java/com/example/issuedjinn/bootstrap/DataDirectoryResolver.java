package com.example.issuedjinn.bootstrap;

import java.nio.file.Path;
import java.util.function.UnaryOperator;

/**
 * Resolves the install's data directory before Quarkus boots, so the directory
 * can be created ahead of the JDBC connection (Flyway's migrate-at-start opens
 * it during runtime init, before any CDI event fires).
 * <p>
 * Mirrors the expression in {@code application.properties}:
 * {@code issue-djinn.data.dir=${ISSUE_DJINN_DATA_DIR:${user.home}/.issue-djinn}}.
 * Quarkus config is not available this early — initializing it before the
 * bootstrap caches a wrong config instance — so the env-var name and the
 * default are mirrored here, and {@code DataDirectoryResolverTest} pins the
 * two sides together: it fails when either the properties line or this class
 * changes without the other.
 */
public final class DataDirectoryResolver {

    private static final String DATA_DIR_ENV_VAR = "ISSUE_DJINN_DATA_DIR";

    private DataDirectoryResolver() {
    }

    /** The data directory, from the {@code ISSUE_DJINN_DATA_DIR} environment variable or the default. */
    public static Path resolve() {
        return resolve(System::getenv);
    }

    /** The resolution rules, parameterized on the environment lookup for testing. */
    static Path resolve(UnaryOperator<String> envLookup) {
        String configured = envLookup.apply(DATA_DIR_ENV_VAR);
        if (configured != null) {
            return Path.of(configured);
        }
        return Path.of(System.getProperty("user.home"), ".issue-djinn");
    }
}