package com.example.issuedjinn;

import com.example.issuedjinn.bootstrap.DatabaseDirectoryCreator;
import com.example.issuedjinn.bootstrap.DataDirectoryResolver;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

/**
 * Quarkus application entrypoint.
 *
 * Quarkus auto-discovers the application and does not require an explicit main method
 * for bootstrapping. This class is provided for completeness and documentation purposes.
 */
@QuarkusMain
public class Application {

    public static void main(String[] args) {
        ensureDataDirectory();
        Quarkus.run(args);
    }

    /**
     * Creates the data directory ahead of the boot: Flyway's migrate-at-start opens the
     * JDBC connection during runtime init, before any CDI event fires, so the directory
     * must exist before {@link Quarkus#run}. On failure the user gets one actionable
     * line naming the directory and the reason, not the stack trace Flyway would print.
     */
    private static void ensureDataDirectory() {
        try {
            DatabaseDirectoryCreator.ensureExists(DataDirectoryResolver.resolve());
        } catch (RuntimeException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
