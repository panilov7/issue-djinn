package com.example.issuedjinn.rest.api;

import com.example.issuedjinn.domain.entity.Issue;
import com.example.issuedjinn.domain.repository.CommentRepository;
import com.example.issuedjinn.domain.repository.IssueRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the REST sort contract on the listing and detail endpoints:
 * {@code sort}/{@code direction} order parent-level rows in grouped and flat
 * modes, {@code child_direction} orders children wherever they are served, a
 * {@code ?parent=N} listing obeys {@code child_direction} alone, and the
 * whitelisted values are the contract — anything unrecognized is a 400.
 *
 * <p>Every rule here is part of the public API surface: the UI, the MCP tools
 * and plain curl all reach these endpoints, so a behaviour change here is a
 * breaking change for all of them at once.
 */
@QuarkusTest
public class IssueListSortTest {

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

    /**
     * Writes an issue directly through the repository with pinned timestamps —
     * the API cannot control {@code createdAt}/{@code updatedAt}, and the sort
     * contract needs orders the ids cannot produce on their own.
     */
    @Transactional
    Long seedIssue(String title, Instant createdAt, Instant updatedAt) {
        Issue issue = new Issue();
        issue.title = title;
        issue.createdAt = createdAt;
        issue.updatedAt = updatedAt;
        issueRepository.persist(issue);
        return issue.id;
    }

    /**
     * Writes a child of the given parent with a pinned creation timestamp —
     * same reason as {@link #seedIssue}: the child order assertions need
     * creation orders the burst-paced API cannot guarantee.
     */
    @Transactional
    Long seedChild(String title, long parentId, Instant createdAt) {
        Issue parent = issueRepository.findById(parentId);
        Issue child = new Issue();
        child.title = title;
        child.parent = parent;
        child.createdAt = createdAt;
        child.updatedAt = createdAt;
        issueRepository.persist(child);
        return child.id;
    }

    /** GETs the given path with the paired query parameters, asserting the expected status. */
    private Response get(int expectedStatus, String path, String... queryParams) {
        RequestSpecification request = given();
        for (int i = 0; i < queryParams.length; i += 2) {
            request = request.queryParam(queryParams[i], queryParams[i + 1]);
        }
        return request
                .when().get(path)
                .then()
                .statusCode(expectedStatus)
                .extract().response();
    }

    /** GETs the given path expecting success. */
    private Response get(String path, String... queryParams) {
        return get(200, path, queryParams);
    }

    /** Runs a list query expecting success and returns the response, whose body holds the rows. */
    private Response listResponse(String... queryParams) {
        return get("/api/issues", queryParams);
    }

    /** Runs a list query expecting success and returns the ids of the rows it returned. */
    private List<Long> listIds(String... queryParams) {
        return listResponse(queryParams).jsonPath().getList("issues.id", Long.class);
    }

    /** The row of the given issue in a list query, with its embedded children. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> rowOf(long issueId, String... queryParams) {
        List<Map<String, Object>> rows = listResponse(queryParams).jsonPath().getList("issues");
        return rows.stream()
                .filter(row -> ((Number) row.get("id")).longValue() == issueId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Issue " + issueId + " has no row in the list"));
    }

    /** The ids of the children the given issue's row embeds, in the order it embeds them. */
    private List<Long> embeddedChildIdsOf(long issueId, String... queryParams) {
        Object children = rowOf(issueId, queryParams).get("children");
        return ((List<Map<String, Object>>) children).stream()
                .map(child -> ((Number) child.get("id")).longValue())
                .toList();
    }

    /** The ids of the children the detail endpoint embeds, in the order it embeds them. */
    private List<Long> detailChildIds(long parentId, String... queryParams) {
        return get("/api/issues/" + parentId, queryParams).jsonPath().getList("children.id", Long.class);
    }

    /** The {@code error.code} of a request rejected with the expected 400. */
    private String errorCodeOf(String path, String... queryParams) {
        return get(400, path, queryParams).jsonPath().getString("error.code");
    }

    // ── sort + direction on parent rows ───────────────────────────────────

    /**
     * Four roots whose id order, createdAt order, updatedAt order and title
     * order all differ — so each sort field proves itself against the id order
     * the rows would otherwise fall into. Two of them share a title, which is
     * what lets the tie-breaker test below ride on the same seeds.
     */
    private Instant base;
    private Long zuluId, alphaId, yankeeFirstId, yankeeSecondId;

    private void seedSortableRoots() {
        base = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        // Persist order fixes the ids: zulu, alpha, yankee-first, yankee-second.
        zuluId = seedIssue("Parent zulu", base.plus(3, ChronoUnit.MINUTES), base.plus(3, ChronoUnit.MINUTES));
        alphaId = seedIssue("Parent alpha", base.plus(2, ChronoUnit.MINUTES), base.plus(0, ChronoUnit.MINUTES));
        yankeeFirstId = seedIssue("Yankee issue", base.plus(1, ChronoUnit.MINUTES), base.plus(2, ChronoUnit.MINUTES));
        yankeeSecondId = seedIssue("Yankee issue", base.plus(0, ChronoUnit.MINUTES), base.plus(1, ChronoUnit.MINUTES));
    }

    /** All four whitelisted fields re-order the grouped root rows, in both directions. */
    @Test
    void sortAndDirectionReorderGroupedRootRows() {
        seedSortableRoots();

        // createdAt: default order is createdAt desc, so asc must be its flip —
        // the opposite of the id order the rows were created in.
        assertThat(listIds("sort", "createdAt", "direction", "desc"))
                .containsExactly(zuluId, alphaId, yankeeFirstId, yankeeSecondId);
        assertThat(listIds("sort", "createdAt", "direction", "asc"))
                .containsExactly(yankeeSecondId, yankeeFirstId, alphaId, zuluId);

        // updatedAt orders differently from both createdAt and id.
        assertThat(listIds("sort", "updatedAt", "direction", "asc"))
                .containsExactly(alphaId, yankeeSecondId, yankeeFirstId, zuluId);
        assertThat(listIds("sort", "updatedAt", "direction", "desc"))
                .containsExactly(zuluId, yankeeFirstId, yankeeSecondId, alphaId);

        // title asc is lexical; desc is its flip with the id tie-breaker
        // traveling with the direction — yankee-second before yankee-first.
        assertThat(listIds("sort", "title", "direction", "asc"))
                .containsExactly(alphaId, zuluId, yankeeFirstId, yankeeSecondId);
        assertThat(listIds("sort", "title", "direction", "desc"))
                .containsExactly(yankeeSecondId, yankeeFirstId, zuluId, alphaId);

        // id is its own sort field; the tie-breaker is the id itself.
        assertThat(listIds("sort", "id", "direction", "asc"))
                .containsExactly(zuluId, alphaId, yankeeFirstId, yankeeSecondId);
        assertThat(listIds("sort", "id", "direction", "desc"))
                .containsExactly(yankeeSecondId, yankeeFirstId, alphaId, zuluId);
    }

    /**
     * The same four fields re-order a flat listing's rows, in both directions —
     * flat is a listing mode, not an order. The expected sequences are the
     * grouped test's: the two modes differ in shape, never in ordering.
     */
    @Test
    void sortAndDirectionReorderFlatRows() {
        seedSortableRoots();

        assertThat(listIds("flat", "true", "sort", "createdAt", "direction", "desc"))
                .containsExactly(zuluId, alphaId, yankeeFirstId, yankeeSecondId);
        assertThat(listIds("flat", "true", "sort", "createdAt", "direction", "asc"))
                .containsExactly(yankeeSecondId, yankeeFirstId, alphaId, zuluId);

        assertThat(listIds("flat", "true", "sort", "updatedAt", "direction", "asc"))
                .containsExactly(alphaId, yankeeSecondId, yankeeFirstId, zuluId);
        assertThat(listIds("flat", "true", "sort", "updatedAt", "direction", "desc"))
                .containsExactly(zuluId, yankeeFirstId, yankeeSecondId, alphaId);

        assertThat(listIds("flat", "true", "sort", "title", "direction", "asc"))
                .containsExactly(alphaId, zuluId, yankeeFirstId, yankeeSecondId);
        assertThat(listIds("flat", "true", "sort", "title", "direction", "desc"))
                .containsExactly(yankeeSecondId, yankeeFirstId, zuluId, alphaId);

        assertThat(listIds("flat", "true", "sort", "id", "direction", "asc"))
                .containsExactly(zuluId, alphaId, yankeeFirstId, yankeeSecondId);
        assertThat(listIds("flat", "true", "sort", "id", "direction", "desc"))
                .containsExactly(yankeeSecondId, yankeeFirstId, alphaId, zuluId);
    }

    /**
     * Two rows that tie on the sort field order by the id tie-breaker, which
     * travels with the direction: ascending puts the lower id first, descending
     * the higher one.
     */
    @Test
    void theIdTieBreakerFlipsWithTheDirection() {
        seedSortableRoots();

        assertThat(listIds("sort", "title", "direction", "asc"))
                .endsWith(yankeeFirstId, yankeeSecondId);
        assertThat(listIds("sort", "title", "direction", "desc"))
                .startsWith(yankeeSecondId, yankeeFirstId);
    }

    /** Omitted parameters mean the documented defaults — parents newest-created-first. */
    @Test
    void omittedSortParametersProduceTheDocumentedDefaults() {
        seedSortableRoots();

        assertThat(listIds()).containsExactly(zuluId, alphaId, yankeeFirstId, yankeeSecondId);
    }

    /**
     * A present-but-empty parameter carries no sort to apply — the codebase's
     * blank-means-absent rule (labels, search) — so it takes the defaults
     * rather than tripping the whitelist.
     */
    @Test
    void blankSortValuesMeanAbsent() {
        seedSortableRoots();

        assertThat(listIds("sort", "", "direction", "", "child_direction", ""))
                .containsExactly(zuluId, alphaId, yankeeFirstId, yankeeSecondId);
    }

    // ── the frontier-flavored combination keeps the global default order ──

    /**
     * The frontier-flavored REST filters — {@code status} + {@code
     * has_open_dependency} + {@code has_assignee} — narrow the listing without
     * disturbing its order: the rows that survive run in the global default
     * (createdAt desc, id desc), not the id order the seeds were persisted in.
     * The claimed issue carries the newest createdAt, so a filter that stopped
     * narrowing would surface it first and fail the sequence, not just add a row.
     */
    @Test
    void aFrontierFilteredListingFollowsTheGlobalDefaultOrder() {
        base = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        // Persist order fixes the ids: newest, middle, claimed, oldest — the
        // createdAt-desc rule and a plain id-desc rule would disagree on it.
        Long newestId = seedIssue("Frontier newest", base.plus(2, ChronoUnit.MINUTES), base.plus(2, ChronoUnit.MINUTES));
        Long middleId = seedIssue("Frontier middle", base.plus(1, ChronoUnit.MINUTES), base.plus(1, ChronoUnit.MINUTES));
        Long claimedId = seedIssue("Claimed issue", base.plus(3, ChronoUnit.MINUTES), base.plus(3, ChronoUnit.MINUTES));
        Long oldestId = seedIssue("Frontier oldest", base, base);
        given()
                .contentType("application/json")
                .body("{\"assignee\": \"someone\"}")
                .when().patch("/api/issues/" + claimedId)
                .then()
                .statusCode(200);

        assertThat(listIds("status", "open", "has_open_dependency", "false", "has_assignee", "false"))
                .containsExactly(newestId, middleId, oldestId);
    }

    // ── child_direction ───────────────────────────────────────────────────

    /** A root whose children's title order differs from their creation order. */
    private Long parentId;
    private List<Long> childIds;

    private void seedParentWithChildren() {
        parentId = seedIssue("Root with children", Instant.now(), Instant.now());
        Instant created = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        childIds = List.of(
                seedChild("Zulu child", parentId, created.plus(0, ChronoUnit.MINUTES)),
                seedChild("Alpha child", parentId, created.plus(1, ChronoUnit.MINUTES)),
                seedChild("Mike child", parentId, created.plus(2, ChronoUnit.MINUTES)));
    }

    /**
     * child_direction re-orders children on every surface that serves them —
     * the listing's embedded children, the {@code ?parent=N} child listing and
     * the detail endpoint's uncapped children — so a flip flips all three
     * together.
     */
    @Test
    void childDirectionReordersChildrenOnEverySurface() {
        seedParentWithChildren();

        assertThat(embeddedChildIdsOf(parentId, "child_direction", "desc"))
                .containsExactly(childIds.get(2), childIds.get(1), childIds.get(0));
        assertThat(listIds("parent", String.valueOf(parentId), "child_direction", "desc"))
                .containsExactly(childIds.get(2), childIds.get(1), childIds.get(0));
        assertThat(detailChildIds(parentId, "child_direction", "desc"))
                .containsExactly(childIds.get(2), childIds.get(1), childIds.get(0));

        assertThat(embeddedChildIdsOf(parentId, "child_direction", "asc"))
                .containsExactlyElementsOf(childIds);
        assertThat(listIds("parent", String.valueOf(parentId), "child_direction", "asc"))
                .containsExactlyElementsOf(childIds);
        assertThat(detailChildIds(parentId, "child_direction", "asc"))
                .containsExactlyElementsOf(childIds);
    }

    /**
     * A {@code ?parent=N} listing obeys child_direction only: parent
     * sort/direction are silently ignored there, because those rows are
     * children.
     */
    @Test
    void aParentListingIgnoresSortAndDirection() {
        seedParentWithChildren();

        // With sort=title&direction=asc applied, the rows would read Alpha,
        // Mike, Zulu — they keep their child order instead.
        assertThat(listIds("parent", String.valueOf(parentId),
                           "sort", "title", "direction", "asc"))
                .containsExactlyElementsOf(childIds);
        // child_direction still governs, whatever the ignored parent sort says.
        assertThat(listIds("parent", String.valueOf(parentId),
                           "sort", "title", "direction", "asc", "child_direction", "desc"))
                .containsExactly(childIds.get(2), childIds.get(1), childIds.get(0));
    }

    /** Omitted parameters leave children oldest-created-first on every surface. */
    @Test
    void omittedChildDirectionProducesTheDocumentedDefault() {
        seedParentWithChildren();

        assertThat(embeddedChildIdsOf(parentId)).containsExactlyElementsOf(childIds);
        assertThat(listIds("parent", String.valueOf(parentId))).containsExactlyElementsOf(childIds);
        assertThat(detailChildIds(parentId)).containsExactlyElementsOf(childIds);
    }

    /**
     * A grouped listing's parent sort and its {@code child_direction} compose
     * independently: the root rows run the parent sort while each row's
     * embedded children keep the child direction — the two orders never leak
     * into each other (parent rows run {@code parentOrder()}, children run
     * {@code childOrder()}, one rule each).
     */
    @Test
    void groupedParentSortAndChildDirectionComposeIndependently() {
        seedSortableRoots();
        seedParentWithChildren();

        // Parents run title asc (alpha, zulu, then the parent root, then the
        // yankees in id order — their shared title ties) while the parent
        // root's row embeds its children in the flipped child direction.
        assertThat(listIds("sort", "title", "direction", "asc"))
                .containsExactly(alphaId, zuluId, parentId, yankeeFirstId, yankeeSecondId);
        assertThat(embeddedChildIdsOf(parentId, "sort", "title", "direction", "asc",
                                      "child_direction", "desc"))
                .containsExactly(childIds.get(2), childIds.get(1), childIds.get(0));
    }

    // ── The whitelist is the contract ─────────────────────────────────────

    /** An unrecognized value on any of the three parameters is a 400, never a silent fallback. */
    @Test
    void unrecognizedSortValuesAreRejectedWith400() {
        seedSortableRoots();

        assertThat(errorCodeOf("/api/issues", "sort", "size"))
                .isEqualTo("VALIDATION_ERROR");
        assertThat(errorCodeOf("/api/issues", "direction", "newest"))
                .isEqualTo("VALIDATION_ERROR");
        assertThat(errorCodeOf("/api/issues", "child_direction", "newest"))
                .isEqualTo("VALIDATION_ERROR");
    }

    /** The whitelist holds even on a ?parent=N listing that would otherwise ignore the parent sort. */
    @Test
    void anUnrecognizedSortIsRejectedEvenWhereItWouldBeIgnored() {
        seedParentWithChildren();

        assertThat(errorCodeOf("/api/issues", "parent", String.valueOf(parentId), "sort", "size"))
                .isEqualTo("VALIDATION_ERROR");
    }

    /** The detail endpoint validates its child_direction the same way. */
    @Test
    void theDetailEndpointRejectsAnUnrecognizedChildDirection() {
        seedParentWithChildren();

        assertThat(errorCodeOf("/api/issues/" + parentId, "child_direction", "newest"))
                .isEqualTo("VALIDATION_ERROR");
    }
}
