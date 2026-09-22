package com.example.issuedjinn.bootstrap;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Ensures the data directory for the SQLite database file and other application data exists.
 * <p>
 * {@link #ensureExists(Path)} runs from {@code Application.main}, before Quarkus boots, so
 * the directory is in place ahead of Flyway's migrate-at-start — that connection opens
 * during runtime init, before any CDI event fires, which is why the observer alone could
 * never win the race. The observer stays as the fallback for boots that never execute
 * {@code main} (dev mode, {@code @QuarkusTest}), where the Quarkus config chain — not the
 * {@code DataDirectoryResolver} mirror — owns the path.
 */
@ApplicationScoped
public class DatabaseDirectoryCreator {

    @ConfigProperty(name = "issue-djinn.data.dir", defaultValue = "${user.home}/.issue-djinn")
    String dataDir;

    void onStart(@Observes StartupEvent event) {
        ensureExists(Path.of(dataDir));
    }

    /**
     * Creates the data directory if it does not exist, no-op if it does.
     * Fails with a message naming the directory and the reason, so callers that
     * print only {@code getMessage()} stay actionable.
     */
    public static void ensureExists(Path dataDir) {
        if (Files.exists(dataDir)) {
            return;
        }
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create data directory " + dataDir
                    + (e.getMessage() != null ? ": " + e.getMessage() : ""), e);
        }
    }
}
