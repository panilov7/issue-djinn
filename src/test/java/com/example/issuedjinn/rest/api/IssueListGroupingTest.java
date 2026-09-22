package com.example.issuedjinn.rest.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.issuedjinn.domain.entity.Issue;
import com.example.issuedjinn.domain.repository.CommentRepository;
import com.example.issuedjinn.domain.repository.IssueRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the grouped shape of {@code GET /api/issues}: one row per root, that
 * root's filter-matching first-level children riding along under it, and the
 * root kept visible as a dimmed container when only its children match.
 *
 * <p>Every rule here is part of the public API surface: the UI, the MCP tools
 * and plain curl all reach this one endpoint, so a behaviour change here is a
 * breaking change for all of them at once.
 */
@QuarkusTest
public class IssueListGroupingTest {

    @Inject
    IssueRepository issueRepository;

    @Inject
    CommentRepository commentRepository;

    @BeforeEach
    @Transactional
    void cleanup() {
        // Delete in proper order to handle foreign key constraints
        commentRepository.deleteAll();
        issueRepository.deleteAll();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Long createIssue(String title, String description) {
        return createIssue(title, description, null);
    }

    private Long createIssue(String title, String description, List<String> labels) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("description", description);
        if (labels != null) {
            body.put("labels", labels);
        }
        return postIssue(body);
    }

    private Long postIssue(Map<String, Object> body) {
        return given()
                .contentType("application/json")
                .body(body)
                .when().post("/api/issues")
                .then()
                .statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    private Long createChild(String title, long parentId) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("description", "");
        body.put("parentId", parentId);
        return postIssue(body);
    }

    /** PATCHes the given fields onto the issue — also the way a test pins updatedAt. */
    private void patchIssue(long id, Map<String, Object> fields) {
        given()
                .contentType("application/json")
                .body(fields)
                .when().patch("/api/issues/" + id)
                .then()
                .statusCode(200);
    }

    private void closeIssue(long id) {
        given()
                .contentType("application/json")
                .body(Map.of("comment", "closing", "author", "test-agent"))
                .when().post("/api/issues/" + id + "/close")
                .then()
                .statusCode(200);
    }

    /** Makes {@code issueId} depend on {@code dependencyId} (204 on success). */
    private void addDependency(long issueId, long dependencyId) {
        given()
                .contentType("application/json")
                .body(Map.of("dependency_id", dependencyId))
                .when().post("/api/issues/" + issueId + "/dependencies")
                .then()
                .statusCode(204);
    }

    /** Runs a list query and returns the ids of the rows it returned. */
    private List<Long> listIds(String... queryParams) {
        return listResponse(queryParams).jsonPath().getList("issues.id", Long.class);
    }

    /** Runs a list query and returns the response, whose rows carry their grouped children. */
    private Response listResponse(String... queryParams) {
        RequestSpecification request = given();
        for (int i = 0; i < queryParams.length; i += 2) {
            request = request.queryParam(queryParams[i], queryParams[i + 1]);
        }
        return request
                .when().get("/api/issues")
                .then()
                .statusCode(200)
                .extract().response();
    }

    /**
     * The list row of the given issue. Fails with its own message when the issue
     * has no row, so "row missing" reads differently from "row wrong".
     */
    private Map<String, Object> rowOf(long issueId, String... queryParams) {
        List<Map<String, Object>> rows = listResponse(queryParams).jsonPath().getList("issues");
        return rows.stream()
                .filter(row -> ((Number) row.get("id")).longValue() == issueId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Issue " + issueId + " has no row in the list"));
    }

    /** The ids of the children the given issue's row embeds, in the order it embeds them. */
    @SuppressWarnings("unchecked")
    private List<Long> embeddedChildIdsOf(long issueId, String... queryParams) {
        Object children = rowOf(issueId, queryParams).get("children");
        return ((List<Map<String, Object>>) children).stream()
                .map(child -> ((Number) child.get("id")).longValue())
                .toList();
    }

    /**
     * Writes a child one level deeper than the cap allows directly through the
     * repository, bypassing the service rule — the shape of a legacy chain.
     */
    @Transactional
    Long createGrandchild(String title, long parentId) {
        Issue issue = new Issue();
        issue.title = title;
        issue.parent = issueRepository.findById(parentId);
        issueRepository.persist(issue);
        return issue.id;
    }

    // ── The grouped shape ─────────────────────────────────────────────────

    /**
     * A row is a root, and its first-level children ride along under it: the
     * children take no page slot of their own and never appear as their own
     * top-level rows, so the list is exactly Root → its first-level children.
     */
    @Test
    void rowsAreRootsWithTheirFirstChildLevelRidingAlong() {
        Long rootId = createIssue("Root with children", "");
        Long firstChildId = createChild("First child", rootId);
        Long secondChildId = createChild("Second child", rootId);
        createGrandchild("Grandchild", firstChildId);
        Long unrelatedRootId = createIssue("Unrelated root", "");

        // Touching the second child makes it the most recently updated — an
        // updatedAt order would put it first; children follow creation instead.
        patchIssue(secondChildId, Map.of("description", "touched"));

        // Parent-level rows newest-created-first, children oldest-created-first.
        assertThat(listIds()).containsExactly(unrelatedRootId, rootId);
        assertThat(embeddedChildIdsOf(rootId)).containsExactly(firstChildId, secondChildId);
    }

    /** A row with nothing matching to group embeds no children at all. */
    @Test
    void rowsWithoutMatchingChildrenEmbedNone() {
        Long childlessId = createIssue("Childless issue", "");
        Long rootId = createIssue("Root with one child", "");
        Long closedChildId = createChild("Closed child", rootId);
        closeIssue(closedChildId);

        assertThat(embeddedChildIdsOf(childlessId, "status", "open")).isEmpty();
        assertThat(embeddedChildIdsOf(rootId, "status", "open")).isEmpty();
        // The closed child is still there for the filter that matches it.
        assertThat(embeddedChildIdsOf(rootId, "status", "closed")).containsExactly(closedChildId);
    }

    /**
     * The children a row embeds are the light shape: a summary row, with no
     * counts and no children of their own — the one-level rule keeps those empty.
     */
    @Test
    void embeddedChildrenAreTheLightShape() {
        Long rootId = createIssue("Root with children", "");
        createChild("Only child", rootId);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) rowOf(rootId).get("children");
        assertThat(children).hasSize(1);
        assertThat(children.get(0))
                .containsKeys("id", "title", "status", "assignee", "labels", "parentId", "updatedAt")
                .doesNotContainKeys("childCounts", "matchesFilter", "matchingChildCount", "children");
    }

    // ── Children meet the same filters ────────────────────────────────────

    /**
     * The active filters apply to children exactly as they apply to roots: the
     * same status, label, assignee and search predicates, not a child-shaped
     * loosening of them.
     */
    @Test
    void childrenMeetTheSameFiltersTheRootsMeet() {
        Long rootId = createIssue("Root", "", List.of("parent"));
        Long matchingChildId = createChild("Matching child", rootId);
        Long closedChildId = createChild("Closed child", rootId);
        closeIssue(closedChildId);
        Long labelledChildId = createChild("Labelled child", rootId);
        patchIssue(labelledChildId, Map.of("labels", List.of("task")));
        Long assignedChildId = createChild("Assigned child", rootId);
        patchIssue(assignedChildId, Map.of("assignee", "agent-68"));

        assertThat(embeddedChildIdsOf(rootId, "status", "closed")).containsExactly(closedChildId);
        assertThat(embeddedChildIdsOf(rootId, "status", "open"))
                .containsExactlyInAnyOrder(matchingChildId, labelledChildId, assignedChildId);
        assertThat(embeddedChildIdsOf(rootId, "label", "task")).containsExactly(labelledChildId);
        assertThat(embeddedChildIdsOf(rootId, "assignee", "agent-68")).containsExactly(assignedChildId);
        // Three of the four children are unassigned; only the assigned one misses.
        assertThat(embeddedChildIdsOf(rootId, "has_assignee", "false"))
                .containsExactlyInAnyOrder(matchingChildId, closedChildId, labelledChildId);
        assertThat(embeddedChildIdsOf(rootId, "search", "Matching")).containsExactly(matchingChildId);
        // Composed, too: two filters at once narrow the children as they would the roots.
        assertThat(embeddedChildIdsOf(rootId, "status", "open", "label", "task"))
                .containsExactly(labelledChildId);
    }

    /**
     * The dependency filter reaches the children through the same re-anchored
     * predicate: a child blocked by an open dependency is off the frontier, a
     * child with none is on it, so the two do not ride along alike.
     */
    @Test
    void theOpenDependencyFilterReachesTheChildrenToo() {
        Long blockerId = createIssue("Open blocker", "");
        Long rootId = createIssue("Root with blocked and free children", "");
        Long blockedChildId = createChild("Blocked child", rootId);
        addDependency(blockedChildId, blockerId);
        Long freeChildId = createChild("Unblocked child", rootId);

        assertThat(embeddedChildIdsOf(rootId, "has_open_dependency", "false"))
                .containsExactly(freeChildId);
        assertThat(embeddedChildIdsOf(rootId, "has_open_dependency", "true"))
                .containsExactly(blockedChildId);
    }

    // ── The cap and its overflow ──────────────────────────────────────────

    /**
     * A root's row embeds its first ten matching children, oldest-created-first
     * — the cap keeps the oldest ten, not the ten most recently touched — and
     * says how many matched in total: the overflow is the count the cap hid,
     * which the UI turns into an "...and N more" link.
     */
    @Test
    void rowsEmbedTenMatchingChildrenAndCountTheOverflow() {
        Long rootId = createIssue("Root with a dozen children", "");
        List<Long> childIds = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            childIds.add(createChild("Child " + i, rootId));
        }
        // Touching the first child makes it the most recently updated; the cap
        // must still keep the oldest ten.
        patchIssue(childIds.get(0), Map.of("description", "touched"));

        List<Long> embedded = embeddedChildIdsOf(rootId);
        assertThat(embedded).hasSize(10);
        assertThat(embedded).isEqualTo(childIds.subList(0, 10));

        Map<String, Object> row = rowOf(rootId);
        assertThat(row.get("matchingChildCount")).isEqualTo(12);
        assertThat(row.get("matchesFilter")).isEqualTo(true);
    }

    // ── Pagination counts roots ───────────────────────────────────────────

    /**
     * Pagination counts roots only: a page of one is one root, with however many
     * children ride along, and the offset moves between roots rather than rows.
     */
    @Test
    void paginationCountsRootsOnly() {
        Long earlierRootId = createIssue("Earlier root", "");
        Long earlierChildId = createChild("Child of the earlier root", earlierRootId);
        createChild("Another child of the earlier root", earlierRootId);
        Long laterRootId = createIssue("Later root", "");
        Long laterChildId = createChild("Child of the later root", laterRootId);

        // Newest-created-first: the later root takes the first page, its child
        // riding along; the offset moves between roots rather than rows.
        assertThat(listIds("limit", "1")).containsExactly(laterRootId);
        assertThat(embeddedChildIdsOf(laterRootId, "limit", "1")).containsExactly(laterChildId);
        assertThat(listIds("limit", "1", "offset", "1")).containsExactly(earlierRootId);
        assertThat(embeddedChildIdsOf(earlierRootId, "limit", "1", "offset", "1"))
                .hasSize(2)
                .first().isEqualTo(earlierChildId);
    }

    // ── The dimmed container root ─────────────────────────────────────────

    /**
     * A root that misses the filters is still listed — flagged — when at least
     * one of its children matches, so a filtered-in child is never hidden by a
     * filtered-out parent. The flag is what the UI dims the row for.
     */
    @Test
    void aRootThatMissesTheFiltersIsListedForAMatchingChild() {
        Long closedRootId = createIssue("Closed root", "");
        Long openChildId = createChild("Open child of the closed root", closedRootId);
        closeIssue(closedRootId);

        Map<String, Object> row = rowOf(closedRootId, "status", "open");
        assertThat(row.get("matchesFilter")).isEqualTo(false);
        assertThat(embeddedChildIdsOf(closedRootId, "status", "open")).containsExactly(openChildId);
        // The counts still cover every child, matched or not — they answer how
        // much work sits under the root, not how many rows are visible.
        assertThat(row.get("childCounts")).isEqualTo(Map.of("open", 1, "closed", 0, "total", 1));
    }

    /** A matching root is flagged as matching, so the UI has one shape to read. */
    @Test
    void aRootThatMatchesTheFiltersIsFlaggedAsMatching() {
        Long rootId = createIssue("Open root", "");
        createChild("Open child", rootId);

        assertThat(rowOf(rootId, "status", "open").get("matchesFilter")).isEqualTo(true);
    }

    /** A root that matches neither the filters nor through a child is not listed. */
    @Test
    void aRootWithNothingMatchingAnywhereIsOmitted() {
        Long closedRootId = createIssue("Closed root", "");
        Long closedChildId = createChild("Closed child", closedRootId);
        closeIssue(closedChildId);
        closeIssue(closedRootId);
        Long openUnrelatedId = createIssue("Open unrelated issue", "");

        assertThat(listIds("status", "open")).containsExactly(openUnrelatedId);
        // The closed root and its child are there for the filter that matches them.
        assertThat(listIds("status", "closed")).containsExactly(closedRootId);
        assertThat(embeddedChildIdsOf(closedRootId, "status", "closed")).containsExactly(closedChildId);
    }

    // ── No duplicates, no deeper levels ───────────────────────────────────

    /**
     * A child grouped under its root is listed once — under its root, never as
     * its own top-level row — however many roots the page shows.
     */
    @Test
    void aGroupedChildNeverTakesARowOfItsOwn() {
        Long rootId = createIssue("Root", "");
        Long childId = createChild("Child", rootId);
        Long otherRootId = createIssue("Other root", "");
        Long otherChildId = createChild("Other child", otherRootId);

        // The other root was created later, so it is the newer root.
        List<Long> listedIds = listIds();
        assertThat(listedIds).containsExactly(otherRootId, rootId);
        assertThat(listedIds).doesNotContain(childId, otherChildId);
        assertThat(embeddedChildIdsOf(rootId)).containsExactly(childId);
        assertThat(embeddedChildIdsOf(otherRootId)).containsExactly(otherChildId);
    }

    /**
     * Only one level is ever grouped: a legacy chain deeper than the cap
     * shows its root and its first-level child, and the deeper descendant is
     * nowhere in the list — not as a row, not embedded, and not able to pull a
     * root onto the page either.
     */
    @Test
    void legacyDeeperDescendantsAreNotRendered() {
        Long rootId = createIssue("Legacy root", "");
        Long middleId = createChild("Legacy middle", rootId);
        Long deepId = createGrandchild("Legacy deep", middleId);

        // The root is there, its first-level child riding along under it.
        assertThat(listIds("search", "Legacy")).containsExactly(rootId);
        assertThat(embeddedChildIdsOf(rootId)).containsExactly(middleId);
        assertThat(rowOf(rootId).get("matchingChildCount")).isEqualTo(1);

        // The deeper descendant matches only a search of its own, and that
        // search lists nothing: it is neither a row nor a child, and it cannot
        // pull its ancestors onto the page.
        assertThat(listIds("search", "Legacy deep")).isEmpty();
        assertThat(deepId).isNotNull();
    }

    // ── The parent filter stays flat ──────────────────────────────────────

    /**
     * Naming a parent asks for that parent's children as their own rows — how
     * the children of one parent are listed — so nothing is grouped and every match
     * is a row of its own.
     */
    @Test
    void aParentFilterListsTheParentsChildrenAsTheirOwnRows() {
        Long rootId = createIssue("Root", "");
        Long firstChildId = createChild("Child of the root", rootId);
        Long secondChildId = createChild("Second child of the root", rootId);
        Long grandchildId = createGrandchild("Grandchild of the root", firstChildId);

        // A parent listing is a child listing: oldest-created-first.
        assertThat(listIds("parent", String.valueOf(rootId)))
                .containsExactly(firstChildId, secondChildId);
        // A legacy middle issue's own children are still its own rows.
        assertThat(listIds("parent", String.valueOf(firstChildId))).containsExactly(grandchildId);
    }

    // ── One child order on every surface ──────────────────────────────────

    /**
     * The children of one root appear in the same oldest-created-first order on
     * every surface that serves them — the list embed, the {@code ?parent=N}
     * child listing and the detail endpoint's uncapped children — so the
     * preview under a root matches the top of the detail page it links to.
     */
    @Test
    void childrenOrderMatchesAcrossListEmbedParentListingAndDetail() {
        Long rootId = createIssue("Root with three children", "");
        List<Long> childIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            childIds.add(createChild("Child " + i, rootId));
        }
        // Touching the middle child makes it the most recently updated; the
        // creation order must survive the touch on every surface.
        patchIssue(childIds.get(1), Map.of("description", "touched"));

        assertThat(embeddedChildIdsOf(rootId)).containsExactlyElementsOf(childIds);
        assertThat(listIds("parent", String.valueOf(rootId))).containsExactlyElementsOf(childIds);

        List<Long> detailChildren = given()
                .when().get("/api/issues/" + rootId)
                .then().statusCode(200)
                .extract().jsonPath().getList("children.id", Long.class);
        assertThat(detailChildren).containsExactlyElementsOf(childIds);
    }

    // ── The flat opt-out ──────────────────────────────────────────────────

    /**
     * {@code flat=true} lists every matching issue as its own row, whatever its
     * depth and however many siblings it has — the shape a picker needs, where a
     * cap on embedded children would silently hide pickable issues.
     */
    @Test
    void aFlatQueryListsEveryMatchingIssueAsItsOwnRow() {
        Long rootId = createIssue("Flat root", "");
        Long firstChildId = createChild("Flat first child", rootId);
        Long secondChildId = createChild("Flat second child", rootId);
        Long grandchildId = createGrandchild("Flat grandchild", firstChildId);

        List<Long> flatIds = listIds("flat", "true");
        assertThat(flatIds)
                .containsExactlyInAnyOrder(rootId, firstChildId, secondChildId, grandchildId);
        // A flat query that names no parent is still a parent-level listing:
        // its rows are newest-created-first, however deep each one sits.
        assertThat(flatIds).containsExactly(grandchildId, secondChildId, firstChildId, rootId);
        for (long id : List.of(rootId, firstChildId, secondChildId)) {
            Map<String, Object> row = rowOf(id, "flat", "true");
            assertThat(row.get("matchesFilter")).isEqualTo(true);
            assertThat(row.get("matchingChildCount")).isEqualTo(0);
            assertThat(row.get("children")).isEqualTo(List.of());
        }
    }
}
