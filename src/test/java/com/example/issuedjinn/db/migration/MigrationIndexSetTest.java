package com.example.issuedjinn.db.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The index contract the V2 ordering migration encodes, pinned
 * where the rest of the suite cannot see it: the app's test datasource runs
 * Hibernate drop-and-create over the Flyway schema, so no {@code @QuarkusTest}
 * observes these indexes. Here the real migration files run through Flyway
 * against a temp SQLite database, and the landing state is checked literally:
 * the five composites added, the two retired indexes gone, the rest of V1
 * kept.
 */
class MigrationIndexSetTest {

    private static final String V1_MIGRATION = "/db/migration/V1__initial_schema.sql";

    /**
     * The index set a V1+V2 database must end up with, per table — the kept V1
     * indexes plus V2's composites, with the two retired names absent. Listed
     * in {@code name} order, the order the {@code sqlite_master} query returns.
     */
    private static final Map<String, List<String>> EXPECTED_INDEXES = Map.of(
            "issue_dependencies", List.of("idx_issue_dependencies_dependency_id"),
            "issue_labels", List.of("idx_issue_labels_label"),
            "comments", List.of("idx_comments_issue"),
            "issues", List.of(
                    "idx_issues_created_at_id",
                    "idx_issues_parent",
                    "idx_issues_parent_created_at_id",
                    "idx_issues_status_assignee",
                    "idx_issues_title_id",
                    "idx_issues_updated_at_id"));

    /**
     * V2's five composites and the column order each carries — the id
     * tie-break folded into the index itself.
     */
    private static final Map<String, List<String>> V2_COMPOSITES = Map.ofEntries(
            Map.entry("idx_issues_parent_created_at_id", List.of("parent_id", "created_at", "id")),
            Map.entry("idx_issues_created_at_id", List.of("created_at", "id")),
            Map.entry("idx_issues_updated_at_id", List.of("updated_at", "id")),
            Map.entry("idx_issues_title_id", List.of("title", "id")),
            Map.entry("idx_issue_dependencies_dependency_id", List.of("dependency_id", "dependent_id")));

    /**
     * An existing V1 database upgrades cleanly: V1 alone first (a filesystem
     * location holding a copy of the V1 resource — an install that predates
     * V2), then the real classpath migrations on top, where the copied V1's
     * checksum matches and only V2 applies.
     */
    @Test
    void anExistingV1DatabaseUpgradesToThePinnedIndexSet(@TempDir Path tempDir) throws Exception {
        Path v1 = tempDir.resolve("V1__initial_schema.sql");
        try (InputStream in = getClass().getResourceAsStream(V1_MIGRATION)) {
            Files.copy(in, v1);
        }

        flyway("filesystem:" + tempDir, tempDir).migrate();
        flyway("classpath:db/migration", tempDir).migrate();

        assertPinnedLandingState(tempDir);
    }

    /** A fresh install: an empty database migrating straight through the classpath V1 + V2. */
    @Test
    void aFreshInstallAppliesV1AndV2ToThePinnedIndexSet(@TempDir Path tempDir) throws Exception {
        flyway("classpath:db/migration", tempDir).migrate();

        assertPinnedLandingState(tempDir);
    }

    /** The temp database's JDBC URL — the one file both paths land in. */
    private static String dbUrl(Path dbDir) {
        return "jdbc:sqlite:" + dbDir.resolve("issue-djinn.db");
    }

    private static Flyway flyway(String locations, Path dbDir) {
        return Flyway.configure()
                .dataSource(dbUrl(dbDir), null, null)
                .locations(locations)
                .load();
    }

    /**
     * The landing state both paths share: V1 and V2 both recorded as applied,
     * and the pinned index set — names per table, then each composite's column
     * order.
     */
    private static void assertPinnedLandingState(Path tempDir) throws Exception {
        try (Connection connection = DriverManager.getConnection(dbUrl(tempDir))) {
            assertAppliedVersions(connection);
            assertIndexNames(connection);
            assertCompositeColumnOrder(connection);
        }
    }

    /** V1 and V2, in install order, both successful. */
    private static void assertAppliedVersions(Connection connection) throws Exception {
        List<String> versions = new ArrayList<>();
        try (ResultSet rows = connection.createStatement().executeQuery(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank")) {
            while (rows.next()) {
                versions.add(rows.getString(1));
            }
        }
        assertEquals(List.of("1", "2"), versions, "applied migration versions");
    }

    /** Per table, exactly the expected explicit indexes — adds present, retired gone. */
    private static void assertIndexNames(Connection connection) throws Exception {
        for (Map.Entry<String, List<String>> expected : EXPECTED_INDEXES.entrySet()) {
            List<String> actual = queryOneStringColumn(connection,
                    "SELECT name FROM sqlite_master"
                            + " WHERE type = 'index' AND tbl_name = ?"
                            + " AND name NOT LIKE 'sqlite_autoindex%' ORDER BY name",
                    expected.getKey());
            assertEquals(expected.getValue(), actual, "indexes on " + expected.getKey());
        }
    }

    /** Each composite carries its columns in the order the ordering rule needs. */
    private static void assertCompositeColumnOrder(Connection connection) throws Exception {
        for (Map.Entry<String, List<String>> composite : V2_COMPOSITES.entrySet()) {
            List<String> columns = queryOneStringColumn(connection,
                    "SELECT name FROM pragma_index_info(?) ORDER BY seqno", composite.getKey());
            assertEquals(composite.getValue(), columns, "columns of " + composite.getKey());
        }
    }

    /** The first column of a one-parameter single-column query, in row order. */
    private static List<String> queryOneStringColumn(Connection connection, String sql, String parameter)
            throws Exception {
        List<String> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    values.add(rows.getString(1));
                }
            }
        }
        return values;
    }
}
