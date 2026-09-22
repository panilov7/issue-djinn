package com.example.issuedjinn.rest.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.example.issuedjinn.domain.entity.Issue;
import com.example.issuedjinn.domain.repository.CommentRepository;
import com.example.issuedjinn.domain.repository.IssueRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Pins the {@code GET /api/issues} query contract: the filters, the search, and
 * the per-root child counts each row embeds.
 *
 * Every rule here is part of the public API surface: the UI, the MCP tools and
 * plain curl all reach this one endpoint, so a behaviour change here is a
 * breaking change for all of them at once.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class IssueListFilterTest {

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

    /** PATCHes the given fields onto the issue. */
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

    private void addDependency(long dependentId, long dependencyId) {
        given()
                .contentType("application/json")
                .body(Map.of("dependency_id", dependencyId))
                .when().post("/api/issues/" + dependentId + "/dependencies")
                .then()
                .statusCode(204);
    }

    /** Runs a list query and returns the ids of the issues it returned. */
    private List<Long> listIds(String... queryParams) {
        return listResponse(queryParams).jsonPath().getList("issues.id", Long.class);
    }

    /** Runs a list query and returns the response, whose body holds the rows and their embedded counts. */
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
     * The {@code childCounts} of the given issue's row in a list query, with the
     * JSON numbers widened to longs. Fails with its own message when the issue is
     * not listed, so "row missing" reads differently from "counts wrong".
     */
    @SuppressWarnings("unchecked")
    private Map<String, Long> childCountsOf(long issueId, String... queryParams) {
        List<Map<String, Object>> rows = listResponse(queryParams).jsonPath().getList("issues");
        Map<String, Object> row = rows.stream()
                .filter(candidate -> ((Number) candidate.get("id")).longValue() == issueId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Issue " + issueId + " has no row in the list"));
        Map<String, Object> counts = (Map<String, Object>) row.get("childCounts");
        if (counts == null) {
            throw new AssertionError("Issue " + issueId + " has no childCounts in its row");
        }
        return asLongs(counts);
    }

    private static Map<String, Long> asLongs(Map<String, Object> counts) {
        return counts.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> ((Number) entry.getValue()).longValue()));
    }

    /**
     * Writes a chain deeper than the cap directly through the repository,
     * bypassing the service rule — the shape of a legacy chain.
     *
     * @return the ids of the chain, root first and deepest last
     */
    @Transactional
    List<Long> createLegacyChain(String titlePrefix, int length) {
        List<Long> ids = new ArrayList<>();
        Issue previous = null;
        for (int i = 0; i < length; i++) {
            Issue issue = new Issue();
            issue.title = titlePrefix + " " + i;
            issue.parent = previous;
            issueRepository.persist(issue);
            ids.add(issue.id);
            previous = issue;
        }
        return ids;
    }

    private List<Long> searchIds(String term) {
        return listIds("search", term);
    }

    // ── Search matches issue ids ──────────────────────────────────────────

    /**
     * The id predicate is integer equality: {@code 36} and {@code #36} both
     * return issue #36, on top of any title/description substring match.
     */
    @Test
    void searchMatchesAnIssueIdExactly() {
        Long byId = createIssue("Alpha", "no digits here");
        // The second issue's text names the first one's id, so the search must
        // return one issue by id and one by text — the "+ any substring match"
        // half of the contract.
        Long byText = createIssue("Contains " + byId + " in the text", "mentions " + byId + " twice");

        assertThat(searchIds(String.valueOf(byId))).containsExactlyInAnyOrder(byId, byText);
        assertThat(searchIds("#" + byId)).containsExactlyInAnyOrder(byId, byText);
    }

    /**
     * Runs first so it sees a fresh id sequence: a single-digit id that is also
     * a substring of the later two-digit ids is the only case that separates
     * integer equality from a substring match over the id column.
     */
    @Test
    @Order(1)
    void searchDoesNotMatchIdsBySubstring() {
        // Digit-free titles and descriptions: a later match can then only come
        // from the id column, never from the text.
        List<Long> ids = List.of(
                "one", "two", "three", "four", "five", "six",
                "seven", "eight", "nine", "ten", "eleven", "twelve").stream()
                .map(number -> createIssue("Numeric issue " + number, "no digits in the text"))
                .toList();

        long target = ids.get(0);

        // Skip (rather than fail) if a previous test class already consumed the
        // low ids — without a one-digit id this test has nothing to discriminate.
        assumeTrue(target <= 9, "needs a single-digit id to be discriminating");

        List<Long> substringOnlyIds = ids.stream()
                .filter(id -> id != target && String.valueOf(id).contains(String.valueOf(target)))
                .toList();
        assertThat(substringOnlyIds).isNotEmpty();

        assertThat(searchIds(String.valueOf(target))).containsExactly(target);
        assertThat(searchIds("#" + target)).containsExactly(target);
    }

    /** A mixed query is a plain text search — it never gains the id predicate. */
    @Test
    void mixedQueryDoesNotGetTheIdPredicate() {
        Long issueId = createIssue("Alpha", "no digits here");

        // "<id> fix" is not a substring of this issue's text, so a text-only
        // search returns nothing; an id predicate would have returned it.
        assertThat(searchIds(issueId + " fix")).isEmpty();
        assertThat(searchIds("#" + issueId + " fix")).isEmpty();
    }

    /**
     * Search is a case-insensitive substring over title and description only.
     * No other column — including labels, assignee and comments — takes part.
     */
    @Test
    void searchIsCaseInsensitiveAndCoversOnlyTitleAndDescription() {
        Long inTitle = createIssue("QUARKUS in the title", "nothing to see");
        Long inDescription = createIssue("nothing to see", "uppercase QUARKUS in the body");
        Long inAssignee = createIssue("quiet issue", "");
        patchIssue(inAssignee, Map.of("assignee", "quarkus-fan"));
        Long inLabel = createIssue("another quiet issue", "", List.of("quarkus"));
        Long inComment = createIssue("yet another quiet issue", "");
        given()
                .contentType("application/json")
                .body(Map.of("author", "quarkus-fan", "body", "mentions quarkus"))
                .when().post("/api/issues/" + inComment + "/comments")
                .then()
                .statusCode(201);

        assertThat(searchIds("quarkus")).containsExactlyInAnyOrder(inTitle, inDescription);
    }

    // ── Search term normalisation ─────────────────────────────────────────

    /**
     * Surrounding whitespace is stripped before the term is parsed, so the
     * search box — which does not trim — cannot turn a trailing space into an
     * empty result. {@code " 36 "} and {@code " #36 "} name issue #36 exactly
     * as {@code 36} and {@code #36} do.
     *
     * <p>Pinned at this HTTP boundary rather than on
     * {@code IssueQuery.normalizedSearch()} because the untrimmed text arrives
     * here, and the strip has to survive RESTEasy's decoding and the
     * repository's parameter binding.
     */
    @Test
    void surroundingWhitespaceAroundAnIntegerStillNamesTheIssue() {
        Long byId = createIssue("Alpha", "no digits here");

        // Digit-free title and description, so a match can only come from the
        // id predicate — the whitespace cannot be rescued by a text match.
        assertThat(searchIds("  " + byId + "  ")).containsExactly(byId);
        assertThat(searchIds(" #" + byId + " ")).containsExactly(byId);
    }

    /**
     * The strip happens once, before the term is used at all, so the *text*
     * half of the search sees the stripped term too: {@code "  quarkus  "}
     * matches "quarkus", not {@code "  quarkus  "}.
     */
    @Test
    void surroundingWhitespaceAroundATextTermIsIgnored() {
        // No whitespace around the term in the title: a LIKE bound to the
        // unstripped term would need " quarkus " to match, and this text
        // has neither a leading nor a trailing space to give it.
        Long inTitle = createIssue("quarkus in the title", "");

        assertThat(searchIds("  quarkus  ")).containsExactly(inTitle);
    }

    /**
     * A term with no non-whitespace character is a blank, not a search — the
     * same rule as a blank {@code label} value. It filters nothing rather than
     * binding a whitespace pattern to match against.
     */
    @Test
    void whitespaceOnlySearchFiltersNothing() {
        Long alpha = createIssue("Alpha", "");
        Long beta = createIssue("Beta", "");

        assertThat(searchIds("   ")).containsExactlyInAnyOrder(alpha, beta);
    }

    // ── Search composes with the other list filters ───────────────────────

    @Test
    void searchComposesWithStatusFilter() {
        Long openId = createIssue("Blocked picker", "");
        Long closedId = createIssue("Blocked picker too", "");
        closeIssue(closedId);

        assertThat(listIds("search", "Blocked picker", "status", "open"))
                .containsExactly(openId);
        assertThat(listIds("search", "Blocked picker", "status", "closed"))
                .containsExactly(closedId);
    }

    @Test
    void searchComposesWithParentFilter() {
        Long parentId = createIssue("Parent", "");
        Long childId = createChild("Shared picker", parentId);
        createIssue("Shared picker outside the parent", "");

        assertThat(listIds("search", "Shared picker", "parent", String.valueOf(parentId)))
                .containsExactly(childId);
    }

    @Test
    void searchComposesWithLabelFilter() {
        Long labelledId = createIssue("Picker work", "", List.of("ready-for-agent"));
        createIssue("Picker work unlabelled", "");

        assertThat(listIds("search", "Picker work", "label", "ready-for-agent"))
                .containsExactly(labelledId);
    }

    @Test
    void searchComposesWithAssigneeFilter() {
        Long assignedId = createIssue("Picker work", "");
        patchIssue(assignedId, Map.of("assignee", "agent-1"));
        Long unassignedId = createIssue("Picker work too", "");

        assertThat(listIds("search", "Picker work", "assignee", "agent-1"))
                .containsExactly(assignedId);
        assertThat(listIds("search", "Picker work", "has_assignee", "false"))
                .containsExactly(unassignedId);
    }

    @Test
    void searchComposesWithOpenDependencyFilter() {
        Long dependencyId = createIssue("Prerequisite", "");
        Long blockedId = createIssue("Picker work blocked", "");
        addDependency(blockedId, dependencyId);
        Long unblockedId = createIssue("Picker work unblocked", "");

        assertThat(listIds("search", "Picker work", "has_open_dependency", "true"))
                .containsExactly(blockedId);
        assertThat(listIds("search", "Picker work", "has_open_dependency", "false"))
                .containsExactly(unblockedId);
    }

    /** Under search, parent-level rows keep the default newest-created-first order. */
    @Test
    void searchResultsKeepTheDefaultNewestCreatedFirstOrder() {
        Long oldest = createIssue("Picker work one", "");
        Long middle = createIssue("Picker work two", "");
        Long newest = createIssue("Picker work three", "");

        // Touching the oldest issue would put it first under an updatedAt order;
        // the default order follows creation, so the newest created still leads.
        patchIssue(oldest, Map.of("description", "touched last"));

        List<Long> ids = searchIds("Picker work");
        assertThat(ids).containsExactly(newest, middle, oldest);
    }

    // ── Label query param ─────────────────────────────────────────────────

    /**
     * A single {@code label} filters by exact match: the issues carrying it, and
     * nothing else — no substring, no case folding, no status narrowing.
     */
    @Test
    void labelFiltersByExactMatch() {
        Long exact = createIssue("Exact label", "", List.of("parent"));
        createIssue("Sublabel", "", List.of("parent:sub"));
        createIssue("Uppercase label", "", List.of("PARENT"));
        createIssue("Unlabelled", "");

        assertThat(listIds("label", "parent")).containsExactly(exact);
    }

    /**
     * Multiple {@code label} params are AND'd (CONTEXT.md "Multiple label
     * parameters"): an issue must carry every listed label, so one label alone
     * is strictly broader than two.
     */
    @Test
    void multipleLabelParametersAreAndComposed() {
        Long bothLabels = createIssue("Picker work", "", List.of("ready-for-agent", "task"));
        Long oneLabel = createIssue("Picker work with one label", "", List.of("ready-for-agent"));
        createIssue("Picker work with neither label", "");

        assertThat(listIds("label", "ready-for-agent", "label", "task"))
                .containsExactly(bothLabels);
        assertThat(listIds("label", "ready-for-agent"))
                .containsExactlyInAnyOrder(bothLabels, oneLabel);
    }

    /**
     * An unknown label is a normal empty result, not an error: {@code 200} with
     * an empty list, never a 400 and never a 404. Labels are opaque strings
     * (ADR-0001) — the server has no whitelist to check a label against, so
     * "no issue uses that label" is the only thing it can say.
     */
    @Test
    void unknownLabelReturnsAnEmptyList() {
        Long labelled = createIssue("Labelled issue", "", List.of("ready-for-agent"));

        assertThat(listIds("label", "no-such-label")).isEmpty();

        // The empty result is a real filter result, not the endpoint giving up:
        // the same data is reachable under the label it does carry.
        assertThat(listIds("label", "ready-for-agent")).containsExactly(labelled);

        // ...and it composes: an unknown label ANDs to nothing even alongside a
        // label that does match.
        assertThat(listIds("label", "ready-for-agent", "label", "no-such-label")).isEmpty();
    }

    /**
     * {@code label} is not status-aware: it filters on labels alone, so a label
     * on a closed issue still matches unless {@code status} says otherwise.
     */
    @Test
    void labelFilterIsNotStatusAware() {
        Long openLabelled = createIssue("Open labelled", "", List.of("ready-for-agent"));
        Long closedLabelled = createIssue("Closed labelled", "", List.of("ready-for-agent"));
        closeIssue(closedLabelled);
        createIssue("Open unlabelled", "");

        assertThat(listIds("label", "ready-for-agent"))
                .containsExactlyInAnyOrder(openLabelled, closedLabelled);
        assertThat(listIds("label", "ready-for-agent", "status", "closed"))
                .containsExactly(closedLabelled);
        assertThat(listIds("label", "ready-for-agent", "status", "open"))
                .containsExactly(openLabelled);
    }

    /**
     * An empty {@code label} value carries no label to match, so it filters
     * nothing — the same rule as an empty {@code search}. It is a blank, not an
     * unknown label, so it does not follow the "unknown value matches nothing"
     * rule that {@code ?status=bogus} follows.
     *
     * <p>RESTEasy drops a truly empty value before it reaches the query, so this
     * pins the observable result; {@link #whitespaceOnlyLabelValueIsIgnored()}
     * pins the case that actually has to be handled in code.
     */
    @Test
    void emptyLabelValueIsIgnored() {
        Long labelled = createIssue("Labelled issue", "", List.of("ready-for-agent"));
        Long unlabelled = createIssue("Unlabelled issue", "");

        // A bare empty value is the same as no label filter at all.
        assertThat(listIds("label", "")).containsExactlyInAnyOrder(labelled, unlabelled);

        // It is dropped from a mixed list too, rather than being AND'd in as an
        // impossible match.
        assertThat(listIds("label", "ready-for-agent", "label", ""))
                .containsExactly(labelled);
    }

    /**
     * A whitespace-only value is the case the blank rule has to be implemented
     * for: unlike a truly empty value, RESTEasy delivers it, so it reaches
     * {@code IssueQuery.normalizedLabels()} and is dropped there instead of
     * being bound as a label to match (which would return nothing).
     */
    @Test
    void whitespaceOnlyLabelValueIsIgnored() {
        Long labelled = createIssue("Labelled issue", "", List.of("ready-for-agent"));
        Long unlabelled = createIssue("Unlabelled issue", "");

        assertThat(listIds("label", "   ")).containsExactlyInAnyOrder(labelled, unlabelled);
        assertThat(listIds("label", "ready-for-agent", "label", "   "))
                .containsExactly(labelled);
    }

    // ── Status query param ────────────────────────────────────────────────

    @Test
    void statusOmittedReturnsBothStatuses() {
        Long openId = createIssue("An open issue", "");
        Long closedId = createIssue("A closed issue", "");
        closeIssue(closedId);

        assertThat(listIds()).containsExactlyInAnyOrder(openId, closedId);
    }

    @Test
    void statusOpenReturnsOnlyOpenIssues() {
        Long openId = createIssue("An open issue", "");
        Long closedId = createIssue("A closed issue", "");
        closeIssue(closedId);

        assertThat(listIds("status", "open")).containsExactly(openId);
    }

    @Test
    void statusClosedReturnsOnlyClosedIssues() {
        Long closedId = createIssue("A closed issue", "");
        createIssue("An open issue", "");
        closeIssue(closedId);

        assertThat(listIds("status", "closed")).containsExactly(closedId);
    }

    /**
     * The undocumented {@code status=all} special case is removed — "omit to
     * list all" is the only path, so an unknown value filters by equality and
     * matches nothing.
     */
    @Test
    void statusAllIsNoLongerASpecialCase() {
        Long openId = createIssue("An open issue", "");
        Long closedId = createIssue("A closed issue", "");
        closeIssue(closedId);

        assertThat(listIds("status", "all")).isEmpty();
    }

    @Test
    void unknownStatusValueReturnsAnEmptyList() {
        Long openId = createIssue("An open issue", "");

        assertThat(listIds("status", "bogus")).isEmpty();
        assertThat(listIds("status", "OPEN")).isEmpty();
    }

    // ── Child counts ──────────────────────────────────────────────────────

    /**
     * Every row embeds the counts of its first-level children, all three buckets
     * together: open, closed, and their total. The counts answer "how much work
     * sits under this root?" — embedded server-side, so listing costs the client
     * one request however many roots the page shows.
     */
    @Test
    void listRowsEmbedFirstChildCounts() {
        Long rootId = createIssue("Root with children", "");
        createChild("Open child", rootId);
        createChild("Another open child", rootId);
        Long closedChild = createChild("Closed child", rootId);
        closeIssue(closedChild);

        assertThat(childCountsOf(rootId))
                .containsOnlyKeys("open", "closed", "total")
                .containsEntry("open", 2L)
                .containsEntry("closed", 1L)
                .containsEntry("total", 3L);
    }

    /** A childless row still embeds the counts, as three zeros rather than an absent object. */
    @Test
    void childlessRowsReportZeroCounts() {
        Long childlessId = createIssue("Childless issue", "");

        assertThat(childCountsOf(childlessId))
                .containsOnlyKeys("open", "closed", "total")
                .containsEntry("open", 0L)
                .containsEntry("closed", 0L)
                .containsEntry("total", 0L);
    }

    /**
     * The counts are unfiltered: whatever the active filter is, a row's counts
     * still cover every first-level child of the root. "Open" and "closed" both
     * get a turn, since each filter lists a different root.
     */
    @Test
    void childCountsIgnoreTheActiveFilters() {
        Long openRootId = createIssue("Open root", "", List.of("parent"));
        createChild("Open child of the open root", openRootId);
        Long closedChildId = createChild("Closed child of the open root", openRootId);
        closeIssue(closedChildId);

        Long closedRootId = createIssue("Closed root", "", List.of("parent"));
        createChild("Open child of the closed root", closedRootId);
        closeIssue(closedRootId);

        Map<String, Long> openRootCounts = Map.of("open", 1L, "closed", 1L, "total", 2L);
        Map<String, Long> closedRootCounts = Map.of("open", 1L, "closed", 0L, "total", 1L);

        assertThat(childCountsOf(openRootId)).isEqualTo(openRootCounts);
        assertThat(childCountsOf(openRootId, "status", "open")).isEqualTo(openRootCounts);
        assertThat(childCountsOf(closedRootId, "status", "closed")).isEqualTo(closedRootCounts);
        assertThat(childCountsOf(openRootId, "search", "Open root")).isEqualTo(openRootCounts);
        assertThat(childCountsOf(openRootId, "label", "parent")).isEqualTo(openRootCounts);
        assertThat(childCountsOf(closedRootId, "label", "parent", "status", "closed"))
                .isEqualTo(closedRootCounts);
        assertThat(childCountsOf(openRootId, "has_assignee", "false")).isEqualTo(openRootCounts);

        // A dependency of the root's is the root's own business, not its children's:
        // the filter that matches on it leaves the counts alone.
        Long unrelatedOpenId = createIssue("Unrelated open issue", "");
        addDependency(openRootId, unrelatedOpenId);
        assertThat(childCountsOf(openRootId, "has_open_dependency", "true")).isEqualTo(openRootCounts);
    }

    /**
     * The counts belong to the rows of the page that returns them: a later
     * page's row carries its own counts, not the first page's.
     */
    @Test
    void childCountsArePageLocal() {
        Long earlierRootId = createIssue("Earlier root", "");
        Long closedChildId = createChild("Closed child of the earlier root", earlierRootId);
        closeIssue(closedChildId);
        Long laterRootId = createIssue("Later root", "");
        createChild("Open child of the later root", laterRootId);
        createChild("Another open child of the later root", laterRootId);

        // Newest-created-first: the later root takes the first page.
        assertThat(childCountsOf(laterRootId, "limit", "1")).containsEntry("total", 2L);
        assertThat(childCountsOf(earlierRootId, "limit", "1", "offset", "1")).containsEntry("total", 1L);
    }

    /**
     * The counts are over first-level children only, matching the one-level rule
     * (CONTEXT.md → Child): a legacy chain deeper than the cap contributes
     * one to the row it hangs from, and the deeper issues belong to that row's
     * counts, never to the root's.
     */
    @Test
    void childCountsCountOnlyFirstLevelChildren() {
        List<Long> chain = createLegacyChain("Legacy issue", 3);
        Long rootId = chain.get(0);
        Long middleId = chain.get(1);

        // Two descendants under the root, one of them first-level.
        assertThat(childCountsOf(rootId))
                .containsEntry("open", 1L)
                .containsEntry("total", 1L);
        // The middle issue is a child row in the grouped list, so it carries no
        // counts of its own there; its counts are visible through the parent
        // filter, which lists it as its own row.
        assertThat(childCountsOf(middleId, "parent", String.valueOf(rootId)))
                .containsEntry("total", 1L);
    }
}
