package com.example.issuedjinn.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The directory-creation half of the bootstrap fix, pinned as behavior: a
 * missing data directory appears, an existing one is left alone (Flyway's
 * database lives inside it), and an uncreatable one fails naming the path —
 * the actionable message {@code Application.main} prints before exiting.
 */
class DatabaseDirectoryCreatorTest {

    @Test
    void createsTheDataDirectoryWhenMissing(@TempDir Path tempDir) {
        Path dataDir = tempDir.resolve("issue-djinn-data");

        DatabaseDirectoryCreator.ensureExists(dataDir);

        assertTrue(Files.isDirectory(dataDir), "data directory should exist");
    }

    @Test
    void leavesAnExistingDataDirectoryAlone(@TempDir Path tempDir) throws IOException {
        Path dataDir = tempDir.resolve("issue-djinn-data");
        Files.createDirectories(dataDir);
        Path existingFile = Files.createFile(dataDir.resolve("issue-djinn.db"));

        DatabaseDirectoryCreator.ensureExists(dataDir);

        assertTrue(Files.exists(existingFile), "existing data directory should keep its contents");
    }

    @Test
    void failsNamingTheDataDirectoryWhenItCannotBeCreated(@TempDir Path tempDir) throws IOException {
        Path plainFile = Files.createFile(tempDir.resolve("not-a-directory"));
        Path dataDirUnderFile = plainFile.resolve("issue-djinn-data");

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> DatabaseDirectoryCreator.ensureExists(dataDirUnderFile));

        assertTrue(failure.getMessage().contains("not-a-directory"),
                "message should name the data directory: " + failure.getMessage());
    }
}