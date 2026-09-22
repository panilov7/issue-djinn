package com.example.issuedjinn.rest.api;

import com.example.issuedjinn.domain.repository.CommentRepository;
import com.example.issuedjinn.domain.repository.IssueRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Integration tests for the SSE event stream at the HTTP seam: subscribe to
 * {@code GET /api/events}, mutate issues through the REST API, and assert the
 * emitted frames.
 */
@QuarkusTest
class IssueEventResourceTest {

    private static final Duration FRAME_TIMEOUT = Duration.ofSeconds(5);

    @TestHTTPResource("/api/events")
    URI eventsUri;

    @Inject
    IssueRepository issueRepository;

    @Inject
    CommentRepository commentRepository;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    @Transactional
    void cleanup() {
        // Delete in proper order to handle foreign key constraints
        commentRepository.deleteAll();
        issueRepository.deleteAll();
    }

    @Test
    void creatingAnIssueViaRestEmitsExactlyOneIssueCreatedFrame() throws Exception {
        try (EventStream stream = EventStream.open(client, eventsUri)) {
            Long id = createIssueViaRest("SSE root issue");

            SseFrame frame = stream.nextEventFrame(FRAME_TIMEOUT);
            assertThat(frame.event()).isEqualTo("issue_created");
            assertThat(frame.id()).isNotBlank();

            JsonNode payload = mapper.readTree(frame.data());
            assertThat(payload.get("type").asText()).isEqualTo("issue_created");
            assertThat(payload.get("seq").asLong()).isEqualTo(Long.parseLong(frame.id()));
            assertThat(payload.get("issueIds")).hasSize(1);
            assertThat(payload.get("issueIds").get(0).asLong()).isEqualTo(id);
            assertThat(Instant.parse(payload.get("at").asText()))
                    .isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));

            // The create emitted one frame and nothing else.
            assertNothingElseEmitted(stream);
        }
    }

    @Test
    void creatingAChildIssueViaRestListsBothIds() throws Exception {
        Long parentId = createIssueViaRest("SSE parent issue");
        try (EventStream stream = EventStream.open(client, eventsUri)) {
            Long childId = createIssueViaRest("SSE child issue", parentId);

            SseFrame frame = stream.nextEventFrame(FRAME_TIMEOUT);
            assertThat(frame.event()).isEqualTo("issue_created");

            JsonNode payload = mapper.readTree(frame.data());
            assertThat(payload.get("issueIds")).hasSize(2);
            assertThat(payload.get("issueIds").get(0).asLong()).isEqualTo(childId);
            assertThat(payload.get("issueIds").get(1).asLong()).isEqualTo(parentId);
        }
    }

    @Test
    void sequenceNumbersAreStrictlyIncreasingAcrossEvents() throws Exception {
        try (EventStream stream = EventStream.open(client, eventsUri)) {
            createIssueViaRest("SSE first issue");
            long firstSeq = Long.parseLong(stream.nextEventFrame(FRAME_TIMEOUT).id());

            createIssueViaRest("SSE second issue");
            long secondSeq = Long.parseLong(stream.nextEventFrame(FRAME_TIMEOUT).id());

            assertThat(secondSeq).isGreaterThan(firstSeq);
        }
    }

    @Test
    void anIdleConnectionReceivesHeartbeatComments() throws Exception {
        try (EventStream stream = EventStream.open(client, eventsUri)) {
            Instant deadline = Instant.now().plus(FRAME_TIMEOUT);
            boolean heartbeatSeen = false;
            while (Instant.now().isBefore(deadline) && !heartbeatSeen) {
                heartbeatSeen = stream.nextFrame(Duration.between(Instant.now(), deadline)).isComment();
            }
            assertThat(heartbeatSeen).as("a heartbeat comment should arrive on an idle connection").isTrue();
        }
    }

    @Test
    void creatingAnIssueViaMcpEmitsTheSameFrame() throws Exception {
        McpStreamableTestClient mcp = openMcp();
        try (EventStream stream = EventStream.open(client, eventsUri)) {
            mcp.when()
                    .toolsCall("create_issue")
                    .withArguments(Map.of("title", "SSE via MCP"))
                    .withAssert(response -> assertThat(response.isError()).isFalse())
                    .send()
                    .thenAssertResults();

            SseFrame frame = stream.nextEventFrame(FRAME_TIMEOUT);
            assertThat(frame.event()).isEqualTo("issue_created");

            JsonNode payload = mapper.readTree(frame.data());
            assertThat(payload.get("type").asText()).isEqualTo("issue_created");
            assertThat(payload.get("issueIds")).hasSize(1);
        } finally {
            mcp.disconnect();
        }
    }

    @Test
    void creatingAChildIssueViaMcpListsBothIds() throws Exception {
        Long parentId = createIssueViaRest("MCP create parent");
        McpStreamableTestClient mcp = openMcp();
        try (EventStream stream = EventStream.open(client, eventsUri)) {
            mcp.when()
                    .toolsCall("create_issue")
                    .withArguments(Map.of("title", "MCP create child", "parent_id", parentId))
                    .withAssert(response -> assertThat(response.isError()).isFalse())
                    .send()
                    .thenAssertResults();

            SseFrame frame = stream.nextEventFrame(FRAME_TIMEOUT);
            assertThat(frame.event()).isEqualTo("issue_created");

            JsonNode payload = mapper.readTree(frame.data());
            assertThat(payload.get("issueIds")).hasSize(2);
            assertThat(payload.get("issueIds").get(0).asLong()).isNotEqualTo(parentId);
            assertThat(payload.get("issueIds").get(1).asLong()).isEqualTo(parentId);
        } finally {
            mcp.disconnect();
        }
    }

    @Test
    void updatingAnIssueViaRestEmitsExactlyOneIssueUpdatedFrame() throws Exception {
        Long id = createIssueViaRest("Issue to update");
        try (EventStream stream = EventStream.open(client, eventsUri)) {
            given()
                    .contentType("application/json")
                    .body(Map.of("title", "Updated title",
                            "description", "Updated description",
                            "labels", List.of("triaged")))
                    .when().patch("/api/issues/" + id)
                    .then()
                    .statusCode(200);

            SseFrame frame = nextUpdatedFrame(stream, id);
            assertThat(mapper.readTree(frame.data()).get("seq").asLong()).isEqualTo(Long.parseLong(frame.id()));

            // The patch emitted one frame and nothing else.
            assertNothingElseEmitted(stream);
        }
    }

    @Test
    void closingAndReopeningAnIssueViaRestEmitsIssueUpdatedWithItsId() throws Exception {
        Long id = createIssueViaRest("Issue to close and reopen");
        try (EventStream stream = EventStream.open(client, eventsUri)) {
            given()
                    .contentType("application/json")
                    .body(Map.of("comment", "done", "author", "tester"))
                    .when().post("/api/issues/" + id + "/close")
                    .then()
                    .statusCode(200);
            nextUpdatedFrame(stream, id);

            given()
                    .contentType("application/json")
                    .body(Map.of("comment", "not done after all", "author", "tester"))
                    .when().post("/api/issues/" + id + "/reopen")
                    .then()
                    .statusCode(200);
            nextUpdatedFrame(stream, id);
        }
    }

    @Test
    void claimingAndUnassigningAnIssueViaRestEmitsIssueUpdatedWithItsId() throws Exception {
        Long id = createIssueViaRest("Issue to claim and unassign");
        try (EventStream stream = EventStream.open(client, eventsUri)) {
            // REST claim is PATCH assignee; unassign is the dedicated endpoint.
            given()
                    .contentType("application/json")
                    .body(Map.of("assignee", "agent-84"))
                    .when().patch("/api/issues/" + id)
                    .then()
                    .statusCode(200);
            nextUpdatedFrame(stream, id);

            given()
                    .when().post("/api/issues/" + id + "/unassign")
                    .then()
                    .statusCode(200);
            nextUpdatedFrame(stream, id);
        }
    }

    @Test
    void updatingAnIssueViaMcpEmitsTheSameFrame() throws Exception {
        Long id = createIssueViaRest("Issue to update via MCP");
        McpStreamableTestClient mcp = openMcp();
        try (EventStream stream = EventStream.open(client, eventsUri)) {
            mcp.when()
                    .toolsCall("update_issue")
                    .withArguments(Map.of("id", id, "title", "MCP updated title"))
                    .withAssert(response -> assertThat(response.isError()).isFalse())
                    .send()
                    .thenAssertResults();

            nextUpdatedFrame(stream, id);
        } finally {
            mcp.disconnect();
        }
    }

    @Test
    void claimingAnIssueViaMcpEmitsIssueUpdatedWithItsId() throws Exception {
        Long id = createIssueViaRest("Issue to claim via MCP");
        McpStreamableTestClient mcp = openMcp();
        try (EventStream stream = EventStream.open(client, eventsUri)) {
            mcp.when()
                    .toolsCall("claim_issue")
                    .withArguments(Map.of("id", id, "agent_name", "agent-84"))
                    .withAssert(response -> assertThat(response.isError()).isFalse())
                    .send()
                    .thenAssertResults();

            nextUpdatedFrame(stream, id);
        } finally {
            mcp.disconnect();
        }
    }

    // ── Hierarchy mutations: reparent and unparent ─────────────────────────

    @Test
    void reparentingAnIssueViaRestListsChildOldAndNewParent() throws Exception {
        Long oldParentId = createIssueViaRest("Old parent");
        Long newParentId = createIssueViaRest("New parent");
        Long childId = createIssueViaRest("Child to move", oldParentId);
        try (EventStream stream = EventStream.open(client, eventsUri)) {
            given()
                    .contentType("application/json")
                    .body(Map.of("parentId", newParentId))
                    .when().patch("/api/issues/" + childId)
                    .then()
                    .statusCode(200);

            nextUpdatedFrame(stream, childId, oldParentId, newParentId);

            // The reparent emitted one frame and nothing else.
            assertNothingElseEmitted(stream);
        }
    }

    @Test
    void unparentingAnIssueViaRestListsChildAndOldParent() throws Exception {
        Long parentId = createIssueViaRest("Parent to leave");
        Long childId = createIssueViaRest("Child to unparent", parentId);
        try (EventStream stream = EventStream.open(client, eventsUri)) {
            // Map.of cannot hold a null value, so the explicit-null body is raw JSON.
            given()
                    .contentType("application/json")
                    .body("{\"parentId\": null}")
                    .when().patch("/api/issues/" + childId)
                    .then()
                    .statusCode(200);

            nextUpdatedFrame(stream, childId, parentId);

            // The unparent emitted one frame and nothing else.
            assertNothingElseEmitted(stream);
        }
    }

    @Test
    void reparentingAnIssueViaMcpEmitsTheSameFrame() throws Exception {
        Long oldParentId = createIssueViaRest("MCP old parent");
        Long newParentId = createIssueViaRest("MCP new parent");
        Long childId = createIssueViaRest("MCP child to move", oldParentId);
        McpStreamableTestClient mcp = openMcp();
        try (EventStream stream = EventStream.open(client, eventsUri)) {
            mcp.when()
                    .toolsCall("update_issue")
                    .withArguments(Map.of("id", childId, "parent_id", newParentId))
                    .withAssert(response -> assertThat(response.isError()).isFalse())
                    .send()
                    .thenAssertResults();

            nextUpdatedFrame(stream, childId, oldParentId, newParentId);
        } finally {
            mcp.disconnect();
        }
    }

    @Test
    void unparentingAnIssueViaMcpEmitsTheSameFrame() throws Exception {
        Long parentId = createIssueViaRest("MCP parent to leave");
        Long childId = createIssueViaRest("MCP child to unparent", parentId);
        McpStreamableTestClient mcp = openMcp();
        try (EventStream stream = EventStream.open(client, eventsUri)) {
            mcp.when()
                    .toolsCall("unparent_issue")
                    .withArguments(Map.of("id", childId))
                    .withAssert(response -> assertThat(response.isError()).isFalse())
                    .send()
                    .thenAssertResults();

            nextUpdatedFrame(stream, childId, parentId);
        } finally {
            mcp.disconnect();
        }
    }

    // ── Dependency mutations: add and remove ───────────────────────────────

    @Test
    void addingADependencyViaRestListsBothEndpoints() throws Exception {
        Long dependencyId = createIssueViaRest("Prerequisite issue");
        Long dependentId = createIssueViaRest("Waiting issue");
        try (EventStream stream = EventStream.open(client, eventsUri)) {
            given()
                    .contentType("application/json")
                    .body(Map.of("dependency_id", dependencyId))
                    .when().post("/api/issues/" + dependentId + "/dependencies")
                    .then()
                    .statusCode(204);

            nextUpdatedFrame(stream, dependentId, dependencyId);

            // The add emitted one frame and nothing else.
            assertNothingElseEmitted(stream);
        }
    }

    @Test
    void removingADependencyViaRestListsBothEndpoints() throws Exception {
        Long dependencyId = createIssueViaRest("Prerequisite to remove");
        Long dependentId = createIssueViaRest("Waiting issue to free");
        addDependencyViaRest(dependentId, dependencyId);
        try (EventStream stream = EventStream.open(client, eventsUri)) {
            given()
                    .when().delete("/api/issues/" + dependentId + "/dependencies/" + dependencyId)
                    .then()
                    .statusCode(204);

            nextUpdatedFrame(stream, dependentId, dependencyId);

            // The remove emitted one frame and nothing else.
            assertNothingElseEmitted(stream);
        }
    }

    @Test
    void addingADependencyViaMcpEmitsTheSameFrame() throws Exception {
        Long dependencyId = createIssueViaRest("MCP prerequisite");
        Long dependentId = createIssueViaRest("MCP waiting issue");
        McpStreamableTestClient mcp = openMcp();
        try (EventStream stream = EventStream.open(client, eventsUri)) {
            mcp.when()
                    .toolsCall("add_dependency")
                    .withArguments(Map.of("dependent_id", dependentId, "dependency_id", dependencyId))
                    .withAssert(response -> assertThat(response.isError()).isFalse())
                    .send()
                    .thenAssertResults();

            nextUpdatedFrame(stream, dependentId, dependencyId);
        } finally {
            mcp.disconnect();
        }
    }

    @Test
    void removingADependencyViaMcpEmitsTheSameFrame() throws Exception {
        Long dependencyId = createIssueViaRest("MCP prerequisite to remove");
        Long dependentId = createIssueViaRest("MCP waiting issue to free");
        addDependencyViaRest(dependentId, dependencyId);
        McpStreamableTestClient mcp = openMcp();
        try (EventStream stream = EventStream.open(client, eventsUri)) {
            mcp.when()
                    .toolsCall("remove_dependency")
                    .withArguments(Map.of("dependent_id", dependentId, "dependency_id", dependencyId))
                    .withAssert(response -> assertThat(response.isError()).isFalse())
                    .send()
                    .thenAssertResults();

            nextUpdatedFrame(stream, dependentId, dependencyId);
        } finally {
            mcp.disconnect();
        }
    }

    /**
     * The backend computes only the direct counterparts of a dependency edge —
     * a lifecycle change to a blocker emits its own id alone, never an
     * enumeration of the issues waiting on it.
     */
    @Test
    void closingABlockerEmitsOnlyItsOwnIdNotItsDependents() throws Exception {
        Long blockerId = createIssueViaRest("Blocker being closed");
        Long dependentId = createIssueViaRest("Dependent of the blocker");
        addDependencyViaRest(dependentId, blockerId);
        try (EventStream stream = EventStream.open(client, eventsUri)) {
            given()
                    .contentType("application/json")
                    .body(Map.of("comment", "done", "author", "tester"))
                    .when().post("/api/issues/" + blockerId + "/close")
                    .then()
                    .statusCode(200);

            nextUpdatedFrame(stream, blockerId);

            // Nothing for the dependent — the close emitted one frame and nothing else.
            assertNothingElseEmitted(stream);
        }
    }

    @Test
    void updatingABlockerEmitsOnlyItsOwnIdNotItsDependents() throws Exception {
        Long blockerId = createIssueViaRest("Blocker being updated");
        Long dependentId = createIssueViaRest("Dependent of the blocker");
        addDependencyViaRest(dependentId, blockerId);
        try (EventStream stream = EventStream.open(client, eventsUri)) {
            given()
                    .contentType("application/json")
                    .body(Map.of("title", "Retitled blocker"))
                    .when().patch("/api/issues/" + blockerId)
                    .then()
                    .statusCode(200);

            nextUpdatedFrame(stream, blockerId);

            // Nothing for the dependent — the patch emitted one frame and nothing else.
            assertNothingElseEmitted(stream);
        }
    }

    /**
     * A request that mutates nothing is not a dependency change: a remove of an
     * edge that was never there (the endpoint already answers 204) emits no
     * frame (events name dependency changes).
     */
    @Test
    void removingAnAbsentDependencyEmitsNoFrame() throws Exception {
        Long dependencyId = createIssueViaRest("Never a prerequisite");
        Long dependentId = createIssueViaRest("Waiting issue");
        try (EventStream stream = EventStream.open(client, eventsUri)) {
            given()
                    .when().delete("/api/issues/" + dependentId + "/dependencies/" + dependencyId)
                    .then()
                    .statusCode(204);

            // The no-op remove emitted nothing — the next frame is a heartbeat.
            assertNothingElseEmitted(stream);
        }
    }

    @Test
    void reAddingAnExistingDependencyEmitsNoFrame() throws Exception {
        Long dependencyId = createIssueViaRest("Prerequisite re-added");
        Long dependentId = createIssueViaRest("Waiting issue");
        addDependencyViaRest(dependentId, dependencyId);
        try (EventStream stream = EventStream.open(client, eventsUri)) {
            addDependencyViaRest(dependentId, dependencyId);

            // The duplicate add emitted nothing — the next frame is a heartbeat.
            assertNothingElseEmitted(stream);
        }
    }

    // ── Comment mutations: a coarse issue_updated ──────────────────────────

    @Test
    void addingACommentViaRestEmitsIssueUpdatedWithItsId() throws Exception {
        Long id = createIssueViaRest("Issue to comment on");
        try (EventStream stream = EventStream.open(client, eventsUri)) {
            given()
                    .contentType("application/json")
                    .body(Map.of("author", "tester", "body", "A comment body"))
                    .when().post("/api/issues/" + id + "/comments")
                    .then()
                    .statusCode(201);

            nextUpdatedFrame(stream, id);

            // The comment emitted one frame and nothing else — no comment-specific
            // event type rides along.
            assertNothingElseEmitted(stream);
        }
    }

    @Test
    void addingACommentViaMcpEmitsTheSameFrame() throws Exception {
        Long id = createIssueViaRest("Issue to comment on via MCP");
        McpStreamableTestClient mcp = openMcp();
        try (EventStream stream = EventStream.open(client, eventsUri)) {
            mcp.when()
                    .toolsCall("comment")
                    .withArguments(Map.of("id", id, "author", "tester", "body", "A comment body"))
                    .withAssert(response -> assertThat(response.isError()).isFalse())
                    .send()
                    .thenAssertResults();

            nextUpdatedFrame(stream, id);
        } finally {
            mcp.disconnect();
        }
    }

    /**
     * Whatever follows the asserted frame is a heartbeat comment — the mutation
     * just tested emitted exactly one event frame.
     */
    private void assertNothingElseEmitted(EventStream stream) throws Exception {
        SseFrame next = stream.nextFrame(FRAME_TIMEOUT);
        assertThat(next.isComment()).as("next frame should be a heartbeat comment, not another event").isTrue();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Long createIssueViaRest(String title) {
        return createIssueViaRest(title, null);
    }

    private Long createIssueViaRest(String title, Long parentId) {
        return given()
                .contentType("application/json")
                .body(parentId == null ? Map.of("title", title) : Map.of("title", title, "parentId", parentId))
                .when().post("/api/issues")
                .then()
                .statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    private void addDependencyViaRest(long dependentId, long dependencyId) {
        given()
                .contentType("application/json")
                .body(Map.of("dependency_id", dependencyId))
                .when().post("/api/issues/" + dependentId + "/dependencies")
                .then()
                .statusCode(204);
    }

    /** A connected MCP streamable client against the test server's {@code /mcp} endpoint. */
    private McpStreamableTestClient openMcp() {
        return McpAssured.newStreamableClient()
                .setMcpPath("/mcp")
                .build()
                .connect();
    }

    /**
     * The next named-event frame, asserted to be an {@code issue_updated} whose
     * {@code issueIds} is exactly the given ids — a bare id for the field and
     * lifecycle mutations, child first then old and new parent for the
     * hierarchy mutations. The frame is returned for the tests that
     * check the payload fields the shared mapping does not pin down.
     */
    private SseFrame nextUpdatedFrame(EventStream stream, long... expectedIds) throws Exception {
        SseFrame frame = stream.nextEventFrame(FRAME_TIMEOUT);
        assertThat(frame.event()).isEqualTo("issue_updated");

        JsonNode payload = mapper.readTree(frame.data());
        assertThat(payload.get("type").asText()).isEqualTo("issue_updated");
        assertThat(payload.get("issueIds")).hasSize(expectedIds.length);
        for (int i = 0; i < expectedIds.length; i++) {
            assertThat(payload.get("issueIds").get(i).asLong()).isEqualTo(expectedIds[i]);
        }
        return frame;
    }

    /** One parsed SSE frame: a named event ({@code event}/{@code id}/{@code data}) or a comment. */
    record SseFrame(String id, String event, String data, String comment) {

        boolean isComment() {
            return comment != null;
        }
    }

    /**
     * A live subscription to the event stream, read frame by frame with timeouts.
     * The stream never completes (it is held open by the server), so every read
     * runs on a daemon reader thread and the test decides how long it is willing
     * to wait for the next line.
     */
    static final class EventStream implements AutoCloseable {

        static EventStream open(HttpClient client, URI uri) throws Exception {
            HttpResponse<InputStream> response = client.send(
                    HttpRequest.newBuilder(uri).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("content-type").orElse(""))
                    .startsWith("text/event-stream");
            return new EventStream(response.body());
        }

        private final InputStream body;
        private final BufferedReader reader;
        private final ExecutorService readerThread = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sse-test-reader");
            thread.setDaemon(true);
            return thread;
        });

        private EventStream(InputStream body) {
            this.body = body;
            this.reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
        }

        /** The next complete frame, whatever it is (comment or named event). */
        SseFrame nextFrame(Duration timeout) throws Exception {
            Instant deadline = Instant.now().plus(timeout);
            String id = null, event = null, data = null, comment = null;
            while (true) {
                String line = nextLine(deadline);
                if (line == null) {
                    throw new AssertionError("Connection closed before a complete frame arrived");
                }
                if (line.isEmpty()) {
                    if (comment != null || data != null) {
                        return new SseFrame(id, event, data, comment);
                    }
                    continue; // stray blank line between frames
                }
                int colon = line.indexOf(':');
                if (colon == 0) {
                    comment = line.substring(1);
                } else if (line.startsWith("id:")) {
                    id = line.substring(3);
                } else if (line.startsWith("event:")) {
                    event = line.substring(6);
                } else if (line.startsWith("data:")) {
                    data = line.substring(5);
                }
            }
        }

        /** The next named-event frame, skipping heartbeat comments. */
        SseFrame nextEventFrame(Duration timeout) throws Exception {
            Instant deadline = Instant.now().plus(timeout);
            while (true) {
                SseFrame frame = nextFrame(Duration.between(Instant.now(), deadline));
                if (!frame.isComment()) {
                    return frame;
                }
            }
        }

        /** The next line, or an assertion error if the deadline passes first. */
        private String nextLine(Instant deadline) throws Exception {
            long millis = Math.max(1, Duration.between(Instant.now(), deadline).toMillis());
            Future<String> pending = readerThread.submit(reader::readLine);
            try {
                return pending.get(millis, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                throw new AssertionError("Timed out after " + millis + " ms waiting for an SSE line", e);
            }
        }

        @Override
        public void close() {
            readerThread.shutdownNow();
            try {
                body.close();
            } catch (IOException ignored) {
                // the point is to unblock the reader; nothing to recover
            }
        }
    }
}