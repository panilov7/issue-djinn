package com.example.issuedjinn.mcp;

import com.example.issuedjinn.domain.entity.Issue;
import com.example.issuedjinn.domain.repository.CommentRepository;
import com.example.issuedjinn.domain.repository.IssueRepository;
import com.example.issuedjinn.domain.service.IssueService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolGuardrails;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.McpError;
import io.quarkiverse.mcp.server.test.McpAssured.ResourceTemplateInfo;
import io.quarkiverse.mcp.server.test.McpAssured.ToolsPage;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for MCP tools and resources over /mcp.
 * Covers all 16 tools and 1 resource template.
 * Uses McpAssured streamable client for JSON-RPC protocol testing.
 */
@QuarkusTest
public class IssueMcpToolsTest {

    @Inject
    IssueService issueService;

    @Inject
    IssueRepository issueRepository;

    @Inject
    CommentRepository commentRepository;

    private McpStreamableTestClient client;

    @BeforeEach
    @Transactional
    void cleanup() {
        // Delete in proper order to handle foreign key constraints
        commentRepository.deleteAll();
        issueRepository.deleteAll();
        
        // Reconnect the MCP client after cleanup
        client = McpAssured.newStreamableClient()
                .setMcpPath("/mcp")
                .build()
                .connect();
    }


    // ── Helper: create test issues via service layer ─────────────────────

    @Transactional
    Long createTestIssueEntity(String title) {
        Issue issue = new Issue();
        issue.title = title;
        issue.description = "";
        issue.status = "open";
        issueRepository.persist(issue);
        return issue.id;
    }

    @Transactional
    Long createTestIssueEntity(String title, String description, String status, String assignee) {
        Issue issue = new Issue();
        issue.title = title;
        issue.description = description != null ? description : "";
        issue.status = status != null ? status : "open";
        issue.assignee = assignee;
        issueRepository.persist(issue);
        return issue.id;
    }

    /**
     * An issue written straight to the store with the createdAt it is given —
     * updatedAt together with it, since nothing here updates the issue after.
     * Ordering tests need real staggered timestamps; a same-millisecond batch
     * would fall to the id tie-breaker and prove nothing about the sort field.
     * A non-null parentId makes it a child of that issue.
     */
    @Transactional
    Long createDatedIssue(String title, Long parentId, java.time.Instant createdAt) {
        Issue issue = new Issue();
        issue.title = title;
        issue.description = "";
        issue.status = "open";
        if (parentId != null) {
            issue.parent = issueRepository.findById(parentId);
        }
        issue.createdAt = createdAt;
        issue.updatedAt = createdAt;
        issueRepository.persist(issue);
        return issue.id;
    }

    // ── 1. Tool discovery ──────────────────────────────────────────────────

    @Test
    public void testAll16ToolsAreRegistered() {
        client.when()
                .toolsList(page -> {
                    assertThat(page.size()).isEqualTo(16);
                    assertThat(page.findByName("list_issues")).isNotNull();
                    assertThat(page.findByName("get_issue")).isNotNull();
                    assertThat(page.findByName("create_issue")).isNotNull();
                    assertThat(page.findByName("update_issue")).isNotNull();
                    assertThat(page.findByName("close_issue")).isNotNull();
                    assertThat(page.findByName("reopen_issue")).isNotNull();
                    assertThat(page.findByName("unassign_issue")).isNotNull();
                    assertThat(page.findByName("unparent_issue")).isNotNull();
                    assertThat(page.findByName("add_dependency")).isNotNull();
                    assertThat(page.findByName("remove_dependency")).isNotNull();
                    assertThat(page.findByName("list_dependencies")).isNotNull();
                    assertThat(page.findByName("list_dependents")).isNotNull();
                    assertThat(page.findByName("list_children")).isNotNull();
                    assertThat(page.findByName("list_frontier")).isNotNull();
                    assertThat(page.findByName("claim_issue")).isNotNull();
                    assertThat(page.findByName("comment")).isNotNull();
                })
                .thenAssertResults();
    }

    @Test
    public void testResourceTemplateIsRegistered() {
        client.when()
                .resourcesTemplatesList(page -> {
                    assertThat(page.size()).isGreaterThanOrEqualTo(1);
                    ResourceTemplateInfo template = page.findByUriTemplate("issue:///{id}");
                    assertThat(template).isNotNull();
                    assertThat(template.description()).contains("tracked issue");
                })
                .thenAssertResults();
    }

    @Test
    public void testToolsHaveDescriptions() {
        client.when()
                .toolsList(page -> {
                    for (var tool : page.tools()) {
                        assertThat(tool.description())
                                .as("Tool '%s' should have a description", tool.name())
                                .isNotBlank();
                    }
                })
                .thenAssertResults();
    }

    @Test
    public void testEveryToolDeclaresUnknownArgsGuardrail() throws Exception {
        // A tool without the guardrail silently drops unknown arguments.
        // Pinned via reflection so a newly added tool cannot skip it.
        for (java.lang.reflect.Method method : IssueMcpTools.class.getDeclaredMethods()) {
            Tool tool = method.getAnnotation(Tool.class);
            if (tool == null) {
                continue;
            }
            ToolGuardrails guardrails = method.getAnnotation(ToolGuardrails.class);
            assertThat(guardrails)
                    .as("Tool '%s' must declare @ToolGuardrails(input = UnknownToolArgsGuardrail.class)", tool.name())
                    .isNotNull();
            assertThat(guardrails.input())
                    .as("Tool '%s' must declare UnknownToolArgsGuardrail", tool.name())
                    .contains(UnknownToolArgsGuardrail.class);
        }
    }

    // ── 2. create_issue ───────────────────────────────────────────────────

    @Test
    public void testCreateIssue() {
        client.when()
                .toolsCall("create_issue")
                .withArguments(Map.of(
                        "title", "MCP Test Issue",
                        "description", "Created via MCP tool"
                ))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    assertThat(response.content()).isNotEmpty();
                    String text = response.firstContent().asText().text();
                    assertThat(text).contains("MCP Test Issue");
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testCreateIssueMinimal() {
        client.when()
                .toolsCall("create_issue")
                .withArguments(Map.of("title", "Minimal Issue"))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    String text = response.firstContent().asText().text();
                    assertThat(text).contains("Minimal Issue");
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testCreateIssueWithLabels() {
        client.when()
                .toolsCall("create_issue")
                .withArguments(Map.of(
                        "title", "Labeled Issue",
                        "labels", java.util.List.of("bug", "urgent")
                ))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    String text = response.firstContent().asText().text();
                    assertThat(text).contains("Labeled Issue");
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testCreateIssueMissingTitle() {
        client.when()
                .toolsCall("create_issue")
                .withArguments(Map.of("description", "No title"))
                .withAssert(response -> {
                    // The response should be an error (isError flag set)
                    assertThat(response.isError() || response.firstContent().asText().text().contains("title")).isTrue();
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testCreateIssueUnderChildRejected() {
        // Setup: the child already sits under the root, so parenting a new issue
        // under the child would grow the hierarchy past one level.
        Long rootId = createTestIssueEntity("MCP Root");
        Long childId = createTestIssueEntity("MCP Child");
        reparentViaService(childId, rootId);

        client.when()
                .toolsCall("create_issue")
                .withArguments(Map.of("title", "MCP Grandchild", "parent_id", childId))
                .withErrorAssert(error -> {
                    assertThat(error.code()).isEqualTo(-32004); // HIERARCHY_DEPTH_EXCEEDED
                })
                .send()
                .thenAssertResults();
    }

    // ── 3. list_issues ─────────────────────────────────────────────────────

    @Test
    public void testListIssues() {
        // Create issues via service
        String alphaTitle = "MCP List Alpha " + System.currentTimeMillis();
        String betaTitle = "MCP List Beta " + System.currentTimeMillis();
        createTestIssueEntity(alphaTitle);
        createTestIssueEntity(betaTitle);

        client.when()
                .toolsCall("list_issues")
                .withArguments(Map.of("search", "MCP List Alpha"))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    String text = response.firstContent().asText().text();
                    assertThat(text).contains(alphaTitle);
                })
                .send()
                .thenAssertResults();

        client.when()
                .toolsCall("list_issues")
                .withArguments(Map.of("search", "MCP List Beta"))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    String text = response.firstContent().asText().text();
                    assertThat(text).contains(betaTitle);
                })
                .send()
                .thenAssertResults();
    }

    /**
     * Each listed row embeds the counts of the issue's first-level children, the
     * same shape the REST list hands over — the counts are not the filters'
     * result size, so a closed child still shows up in them.
     */
    @Test
    public void testListIssuesEmbedsChildCounts() {
        Long rootId = createTestIssueEntity("MCP Counts Root");
        Long openChildId = createTestIssueEntity("MCP Counts Open Child");
        Long closedChildId = createTestIssueEntity("MCP Counts Closed Child", "", "closed", null);
        reparentViaService(openChildId, rootId);
        reparentViaService(closedChildId, rootId);

        client.when()
                .toolsCall("list_issues")
                .withArguments(Map.of("search", "MCP Counts Root"))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    String text = response.firstContent().asText().text();
                    assertThat(text).contains("\"childCounts\":{\"open\":1,\"closed\":1,\"total\":2}");
                })
                .send()
                .thenAssertResults();
    }

    /**
     * The rows an agent lists are grouped one level deep, the same shape the
     * REST list hands over: a root's filter-matching first-level children ride
     * along under it, and a root that misses the filters only for its own status
     * is still listed, flagged as not matching.
     */
    @Test
    public void testListIssuesEmbedsMatchingChildren() {
        Long rootId = createTestIssueEntity("MCP Grouped Root");
        Long openChildId = createTestIssueEntity("MCP Grouped Open Child");
        Long closedChildId = createTestIssueEntity("MCP Grouped Closed Child");
        reparentViaService(openChildId, rootId);
        reparentViaService(closedChildId, rootId);
        Long closedRootId = createTestIssueEntity("MCP Grouped Closed Root", "", "closed", null);
        Long frontierChildId = createTestIssueEntity("MCP Grouped Frontier Child");
        reparentViaService(frontierChildId, closedRootId);

        // Every title here carries "MCP Grouped", so both roots and all three
        // children match the search: both roots are listed as matching.
        client.when()
                .toolsCall("list_issues")
                .withArguments(Map.of("search", "MCP Grouped"))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    String text = allRowsText(response);
                    assertThat(text).contains("MCP Grouped Root");
                    assertThat(text).contains("MCP Grouped Closed Root");
                    assertThat(text).contains("MCP Grouped Open Child");
                    assertThat(text).contains("MCP Grouped Closed Child");
                    assertThat(text).contains("MCP Grouped Frontier Child");
                    assertThat(text).contains("matchesFilter\":true");
                    assertThat(text).doesNotContain("matchesFilter\":false");
                })
                .send()
                .thenAssertResults();

        // A closed parent is still listed for its matching child, flagged as a
        // container — an agent reading the frontier sees the child, not a hole.
        // The search names only the child, so the parent itself does not match.
        client.when()
                .toolsCall("list_issues")
                .withArguments(Map.of("search", "Frontier"))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    String text = allRowsText(response);
                    assertThat(text).contains("MCP Grouped Closed Root");
                    assertThat(text).contains("MCP Grouped Frontier Child");
                    assertThat(text).contains("matchesFilter\":false");
                })
                .send()
                .thenAssertResults();
    }

    /** Every text content of a tools call, joined — one row of the list per content. */
    private String allRowsText(ToolResponse response) {
        StringBuilder rows = new StringBuilder();
        response.content().forEach(content -> rows.append(content.asText().text()));
        return rows.toString();
    }

    /**
     * The MCP tool resolves a search term the same way REST does: a bare or
     * '#' prefixed integer matches the issue with that id, even when the title
     * and description carry no digits for it to match on.
     */
    @Test
    public void testListIssuesSearchMatchesIssueId() {
        String title = "MCP List By Id";
        Long issueId = createTestIssueEntity(title);

        for (String term : List.of(String.valueOf(issueId), "#" + issueId)) {
            client.when()
                    .toolsCall("list_issues")
                    .withArguments(Map.of("search", term))
                    .withAssert(response -> {
                        assertThat(response.isError()).isFalse();
                        String text = response.firstContent().asText().text();
                        assertThat(text).contains(title);
                    })
                    .send()
                    .thenAssertResults();
        }
    }

    @Test
    public void testListIssuesWithStatusFilter() {
        createTestIssueEntity("Open Issue For Filter", null, "open", null);

        client.when()
                .toolsCall("list_issues")
                .withArguments(Map.of("status", "open"))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    String text = response.firstContent().asText().text();
                    assertThat(text).contains("Open Issue For Filter");
                })
                .send()
                .thenAssertResults();
    }

    // ── 4. get_issue ────────────────────────────────────────────────────────

    @Test
    public void testGetIssue() {
        Long id = createTestIssueEntity("Get Test Issue");

        client.when()
                .toolsCall("get_issue")
                .withArguments(Map.of("id", id))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    String text = response.firstContent().asText().text();
                    assertThat(text).contains("Get Test Issue");
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testGetIssueEmbedsChildren() {
        Long parentId = createTestIssueEntity("Parent Issue");
        client.when()
                .toolsCall("create_issue")
                .withArguments(Map.of("title", "Child Issue", "parent_id", parentId))
                .withAssert(response -> assertThat(response.isError()).isFalse())
                .send()
                .thenAssertResults();

        client.when()
                .toolsCall("get_issue")
                .withArguments(Map.of("id", parentId))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    String text = response.firstContent().asText().text();
                    assertThat(text).contains("Child Issue");
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testGetIssueNotFound() {
        client.when()
                .toolsCall("get_issue")
                .withArguments(Map.of("id", 999999L))
                .withErrorAssert(error -> {
                    assertThat(error.code()).isEqualTo(-32602); // INVALID_PARAMS
                })
                .send()
                .thenAssertResults();
    }

    // ── 5. update_issue ───────────────────────────────────────────────────

    @Test
    public void testUpdateIssue() {
        Long id = createTestIssueEntity("Original Title");

        client.when()
                .toolsCall("update_issue")
                .withArguments(Map.of(
                        "id", id,
                        "title", "Updated Title",
                        "description", "Updated description"
                ))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    String text = response.firstContent().asText().text();
                    assertThat(text).contains("Updated Title");
                    assertThat(text).contains("Updated description");
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testUpdateIssueAssignAndLabels() {
        Long id = createTestIssueEntity("Issue For Assign");

        client.when()
                .toolsCall("update_issue")
                .withArguments(Map.of(
                        "id", id,
                        "assignee", "agent-bob",
                        "labels", java.util.List.of("task", "ready-for-agent")
                ))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    String text = response.firstContent().asText().text();
                    assertThat(text).contains("agent-bob");
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testUpdateIssueReparent() {
        Long parentId = createTestIssueEntity("MCP Parent");
        Long childId = createTestIssueEntity("MCP Child");

        client.when()
                .toolsCall("update_issue")
                .withArguments(Map.of(
                        "id", childId,
                        "parent_id", parentId
                ))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    String text = response.firstContent().asText().text();
                    assertThat(text).contains("\"parentId\":" + parentId);
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testUpdateIssueReparentCycleRejected() {
        // Setup: A already sits under B. Moving B under A would close the cycle A -> B -> A.
        // The parent link is created via the service layer so the test does not depend on
        // the ordering of concurrent MCP calls.
        Long issueA = createTestIssueEntity("MCP Issue A");
        Long issueB = createTestIssueEntity("MCP Issue B");
        reparentViaService(issueA, issueB);

        client.when()
                .toolsCall("update_issue")
                .withArguments(Map.of("id", issueB, "parent_id", issueA))
                .withErrorAssert(error -> {
                    assertThat(error.code()).isEqualTo(-32003); // CYCLE_DETECTED
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testUpdateIssueReparentUnderChildRejected() {
        // Setup: the child already sits under the root. Moving another issue under
        // the child would grow the hierarchy past one level.
        Long rootId = createTestIssueEntity("MCP Root");
        Long childId = createTestIssueEntity("MCP Child");
        reparentViaService(childId, rootId);
        Long otherId = createTestIssueEntity("MCP Other");

        client.when()
                .toolsCall("update_issue")
                .withArguments(Map.of("id", otherId, "parent_id", childId))
                .withErrorAssert(error -> {
                    assertThat(error.code()).isEqualTo(-32004); // HIERARCHY_DEPTH_EXCEEDED
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testUnparentIssue() {
        Long parentId = createTestIssueEntity("MCP Unparent Parent");
        Long childId = createTestIssueEntity("MCP Unparent Child");
        reparentViaService(childId, parentId);

        client.when()
                .toolsCall("unparent_issue")
                .withArguments(Map.of("id", childId))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    String text = response.firstContent().asText().text();
                    assertThat(text).contains("\"parentId\":null");
                })
                .send()
                .thenAssertResults();
    }

    @Transactional
    void reparentViaService(Long childId, Long parentId) {
        issueService.reparent(childId, parentId);
    }

    @Test
    public void testToolsCallUnknownArgumentRejected() {
        // Regression test: a client-side rename (parentId instead of parent_id)
        // must fail loudly instead of silently dropping the value.
        Long id = createTestIssueEntity("Issue For Unknown Arg");

        client.when()
                .toolsCall("update_issue")
                .withArguments(Map.of("id", id, "parentId", 1))
                .withAssert(response -> {
                    assertThat(response.isError()).isTrue();
                    String text = response.firstContent().asText().text();
                    assertThat(text).contains("parentId");
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testUnparentIssueNotFound() {
        client.when()
                .toolsCall("unparent_issue")
                .withArguments(Map.of("id", 999999))
                .withErrorAssert(error -> {
                    assertThat(error.code()).isEqualTo(-32602); // INVALID_PARAMS
                })
                .send()
                .thenAssertResults();
    }

    // ── 6. close_issue ─────────────────────────────────────────────────────

    @Test
    public void testCloseIssue() {
        Long id = createTestIssueEntity("Issue To Close");

        client.when()
                .toolsCall("close_issue")
                .withArguments(Map.of(
                        "id", id,
                        "comment", "Closing via MCP tool",
                        "author", "test-agent"
                ))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    String text = response.firstContent().asText().text();
                    assertThat(text).contains("closed");
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testCloseIssueMissingComment() {
        Long id = createTestIssueEntity("Issue Close No Comment " + System.currentTimeMillis());

        client.when()
                .toolsCall("close_issue")
                .withArguments(Map.of("id", id, "author", "test-agent"))
                .withAssert(response -> {
                    // The response should indicate an error - either via isError flag or error text
                    assertThat(response.firstContent().asText().text().toLowerCase()).contains("comment");
                })
                .send()
                .thenAssertResults();
    }

    // ── 7. reopen_issue ─────────────────────────────────────────────────────

    @Test
    public void testReopenIssue() {
        Long id = createTestIssueEntity("Issue To Reopen", "", "closed", null);

        client.when()
                .toolsCall("reopen_issue")
                .withArguments(Map.of(
                        "id", id,
                        "comment", "Reopening via MCP tool",
                        "author", "test-agent"
                ))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    String text = response.firstContent().asText().text();
                    assertThat(text).contains("open");
                })
                .send()
                .thenAssertResults();
    }

    // ── 8. unassign_issue ───────────────────────────────────────────────────

    @Test
    public void testUnassignIssue() {
        Long id = createTestIssueEntity("Assigned Issue", "", "open", "agent-x");

        client.when()
                .toolsCall("unassign_issue")
                .withArguments(Map.of("id", id))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    String text = response.firstContent().asText().text();
                    assertThat(text).contains("null");
                })
                .send()
                .thenAssertResults();
    }

    // ── 9. add_dependency ──────────────────────────────────────────────────

    @Test
    public void testAddDependency() {
        Long dependentId = createTestIssueEntity("Dependent Issue");
        Long dependencyId = createTestIssueEntity("Dependency Issue");

        client.when()
                .toolsCall("add_dependency")
                .withArguments(Map.of(
                        "dependent_id", dependentId,
                        "dependency_id", dependencyId
                ))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    String text = response.firstContent().asText().text();
                    assertThat(text).contains("Dependency added");
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testAddDependencyCycleDetection() {
        Long issueA = createTestIssueEntity("Issue A Cycle");
        Long issueB = createTestIssueEntity("Issue B Cycle");

        // First add A depends on B
        client.when()
                .toolsCall("add_dependency")
                .withArguments(Map.of("dependent_id", issueA, "dependency_id", issueB))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                })
                .send()
                .thenAssertResults();

        // Now try to add B depends on A — should fail with cycle detection
        client.when()
                .toolsCall("add_dependency")
                .withArguments(Map.of("dependent_id", issueB, "dependency_id", issueA))
                .withErrorAssert(error -> {
                    assertThat(error.code()).isEqualTo(-32003); // CYCLE_DETECTED
                })
                .send()
                .thenAssertResults();
    }

    // ── 10. remove_dependency ───────────────────────────────────────────────

    @Test
    public void testRemoveDependency() {
        Long dependentId = createTestIssueEntity("Dependent For Remove");
        Long dependencyId = createTestIssueEntity("Dependency For Remove");

        // First add the dependency
        client.when()
                .toolsCall("add_dependency")
                .withArguments(Map.of("dependent_id", dependentId, "dependency_id", dependencyId))
                .withAssert(response -> assertThat(response.isError()).isFalse())
                .send()
                .thenAssertResults();

        // Then remove it
        client.when()
                .toolsCall("remove_dependency")
                .withArguments(Map.of("dependent_id", dependentId, "dependency_id", dependencyId))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    String text = response.firstContent().asText().text();
                    assertThat(text).contains("Dependency removed");
                })
                .send()
                .thenAssertResults();
    }

    // ── 11. list_dependencies ──────────────────────────────────────────────

    @Test
    public void testListDependencies() {
        Long dependentId = createTestIssueEntity("Dependent For List");
        Long dependencyId = createTestIssueEntity("Dependency For List");

        // Add dependency relationship
        client.when()
                .toolsCall("add_dependency")
                .withArguments(Map.of("dependent_id", dependentId, "dependency_id", dependencyId))
                .withAssert(response -> assertThat(response.isError()).isFalse())
                .send()
                .thenAssertResults();

        // List dependencies of the dependent issue
        client.when()
                .toolsCall("list_dependencies")
                .withArguments(Map.of("id", dependentId))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    String text = response.firstContent().asText().text();
                    assertThat(text).contains("Dependency For List");
                })
                .send()
                .thenAssertResults();
    }

    // ── 12. list_dependents ────────────────────────────────────────────────

    @Test
    public void testListDependents() {
        Long dependentId = createTestIssueEntity("Dependent For Dependents");
        Long dependencyId = createTestIssueEntity("Dependency For Dependents");

        // Add dependency relationship
        client.when()
                .toolsCall("add_dependency")
                .withArguments(Map.of("dependent_id", dependentId, "dependency_id", dependencyId))
                .withAssert(response -> assertThat(response.isError()).isFalse())
                .send()
                .thenAssertResults();

        // List dependents of the dependency issue
        client.when()
                .toolsCall("list_dependents")
                .withArguments(Map.of("id", dependencyId))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    String text = response.firstContent().asText().text();
                    assertThat(text).contains("Dependent For Dependents");
                })
                .send()
                .thenAssertResults();
    }

    // ── 13. list_children ────────────────────────────────────────────────────

    @Test
    public void testListChildren() {
        Long parentId = createTestIssueEntity("Parent Issue");

        // Create children via MCP with parent_id
        client.when()
                .toolsCall("create_issue")
                .withArguments(Map.of(
                        "title", "Child Issue 1",
                        "parent_id", parentId
                ))
                .withAssert(response -> assertThat(response.isError()).isFalse())
                .send()
                .thenAssertResults();

        client.when()
                .toolsCall("list_children")
                .withArguments(Map.of("parent_id", parentId))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    String text = response.firstContent().asText().text();
                    assertThat(text).contains("Child Issue 1");
                })
                .send()
                .thenAssertResults();
    }

    // ── 14. list_frontier ────────────────────────────────────────────────────

    @Test
    public void testListFrontier() {
        // Create an open, unassigned issue with no dependencies — should be on the frontier
        createTestIssueEntity("Frontier Issue", "", "open", null);

        // Create an assigned issue — should NOT be on the frontier
        createTestIssueEntity("Assigned Issue", "", "open", "some-agent");

        client.when()
                .toolsCall("list_frontier")
                .withArguments(Map.of())
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    String text = response.firstContent().asText().text();
                    assertThat(text).contains("Frontier Issue");
                })
                .send()
                .thenAssertResults();
    }

    /**
     * The frontier rows carry the same child counts the issues list embeds: a
     * frontier parent's counts are the work sitting under it, work the frontier
     * itself filters out included.
     */
    @Test
    public void testListFrontierEmbedsChildCounts() {
        Long rootId = createTestIssueEntity("Frontier Root With Children");
        // Closed and assigned both drop an issue off the frontier; the counts of
        // the row that stays see the work anyway.
        Long closedChildId = createTestIssueEntity("Frontier Closed Child", "", "closed", null);
        reparentViaService(closedChildId, rootId);

        client.when()
                .toolsCall("list_frontier")
                .withArguments(Map.of())
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    String text = response.firstContent().asText().text();
                    assertThat(text).contains("\"title\":\"Frontier Root With Children\"");
                    assertThat(text).contains("\"childCounts\":{\"open\":0,\"closed\":1,\"total\":1}");
                })
                .send()
                .thenAssertResults();
    }

    /**
     * The frontier stays a flat set of actionable issues: a frontier child is a
     * row of its own, not a child riding under its root — the tool's contract is
     * the actionable issues themselves, so an agent picks work straight off the
     * list and nothing under a root is hidden by the embedded-children cap.
     */
    @Test
    public void testListFrontierListsChildrenAsTheirOwnRows() {
        Long rootId = createTestIssueEntity("Frontier Flat Root");
        Long childId = createTestIssueEntity("Frontier Flat Child");
        reparentViaService(childId, rootId);

        client.when()
                .toolsCall("list_frontier")
                .withArguments(Map.of())
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    List<String> rows = response.content().stream()
                            .map(content -> content.asText().text())
                            .toList();
                    // The child's row is its own: it names no root, which is what
                    // it would do if it were embedded under the root instead.
                    assertThat(rows).anySatisfy(row -> {
                        assertThat(row).contains("\"title\":\"Frontier Flat Child\"");
                        assertThat(row).doesNotContain("Frontier Flat Root");
                    });
                    assertThat(rows).anySatisfy(row ->
                            assertThat(row).contains("\"title\":\"Frontier Flat Root\""));
                })
                .send()
                .thenAssertResults();
    }

    // ── 14b. Ordering at the MCP seam ────────────────────────────────────────

    /** Every row of a list-tool response, in the order the tool returned them. */
    private List<String> rowTexts(ToolResponse response) {
        return response.content().stream()
                .map(content -> content.asText().text())
                .toList();
    }

    private static final java.util.regex.Pattern ROW_ID = java.util.regex.Pattern.compile("\"id\":(\\d+)");

    /** The id a list row carries, read out of its JSON text. */
    private static long idOf(String row) {
        return Long.parseLong(ROW_ID.matcher(row).results().findFirst().orElseThrow().group(1));
    }

    /** The ids of the rows whose JSON names the given title, in listed order. */
    private List<Long> idsOfRowsWithTitle(ToolResponse response, String title) {
        return rowTexts(response).stream()
                .filter(row -> row.contains(title))
                .map(IssueMcpToolsTest::idOf)
                .toList();
    }

    /** The positions of the given titles inside one JSON document, in document order. */
    private List<Integer> positionsIn(String document, List<String> titles) {
        return titles.stream().map(document::indexOf).toList();
    }

    /**
     * The frontier is the picking list, oldest-created-first with the id
     * tie-breaker, fixed — the "lowest number wins" convention falls
     * out of the list order directly, so an agent takes the first row. An
     * assigned issue is off the frontier and appears nowhere.
     */
    @Test
    public void testListFrontierReturnsOldestFirstWithIdTieBreaker() {
        java.time.Instant now = java.time.Instant.now();
        createTestIssueEntity("Frontier Order Assigned", "", "open", "some-agent");
        Long newest = createDatedIssue("Frontier Order Newest", null, now);
        Long middle = createDatedIssue("Frontier Order Middle", null,
                now.minus(1, java.time.temporal.ChronoUnit.MINUTES));
        Long oldest = createDatedIssue("Frontier Order Oldest", null,
                now.minus(2, java.time.temporal.ChronoUnit.MINUTES));

        client.when()
                .toolsCall("list_frontier")
                .withArguments(Map.of())
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    assertThat(idsOfRowsWithTitle(response, "Frontier Order"))
                            .containsExactly(oldest, middle, newest);
                })
                .send()
                .thenAssertResults();
    }

    /**
     * Same-timestamp frontier issues tie on createdAt and fall to the id
     * tie-breaker, ascending — the order the picking convention reads.
     */
    @Test
    public void testListFrontierTieBreaksOnIdAscending() {
        java.time.Instant shared = java.time.Instant.now();
        Long first = createDatedIssue("Frontier Tie First", null, shared);
        Long second = createDatedIssue("Frontier Tie Second", null, shared);

        client.when()
                .toolsCall("list_frontier")
                .withArguments(Map.of())
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    assertThat(idsOfRowsWithTitle(response, "Frontier Tie"))
                            .containsExactly(first, second);
                })
                .send()
                .thenAssertResults();
    }

    /**
     * The fixed oldest-first order holds with a parent filter too: the frontier of
     * one parent is the same picking list, not a re-sorted view.
     */
    @Test
    public void testListFrontierByParentStaysOldestFirst() {
        java.time.Instant now = java.time.Instant.now();
        Long parentId = createTestIssueEntity("Frontier Parent");
        Long newest = createDatedIssue("Frontier Parent Newest", parentId, now);
        Long middle = createDatedIssue("Frontier Parent Middle", parentId,
                now.minus(1, java.time.temporal.ChronoUnit.MINUTES));
        Long oldest = createDatedIssue("Frontier Parent Oldest", parentId,
                now.minus(2, java.time.temporal.ChronoUnit.MINUTES));

        client.when()
                .toolsCall("list_frontier")
                .withArguments(Map.of("parent_id", parentId))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    assertThat(idsOfRowsWithTitle(response, "Frontier Parent "))
                            .containsExactly(oldest, middle, newest);
                })
                .send()
                .thenAssertResults();
    }

    /**
     * One child order across every child-serving surface: list_children, the
     * get_issue detail, the issue:///{id} resource and the ?parent-style flat
     * rows all serve the same children oldest-created-first — a client reading
     * two of them never sees the same siblings in two different orders.
     */
    @Test
    public void testChildOrderStableAcrossMcpSurfaces() {
        java.time.Instant now = java.time.Instant.now();
        Long parentId = createTestIssueEntity("Order Stability Parent");
        Long newest = createDatedIssue("Order Stability Newest", parentId, now);
        Long middle = createDatedIssue("Order Stability Middle", parentId,
                now.minus(1, java.time.temporal.ChronoUnit.MINUTES));
        Long oldest = createDatedIssue("Order Stability Oldest", parentId,
                now.minus(2, java.time.temporal.ChronoUnit.MINUTES));
        List<Long> oldestFirst = List.of(oldest, middle, newest);

        client.when()
                .toolsCall("list_children")
                .withArguments(Map.of("parent_id", parentId))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    assertThat(idsOfRowsWithTitle(response, "Order Stability "))
                            .containsExactlyElementsOf(oldestFirst);
                })
                .send()
                .thenAssertResults();

        client.when()
                .toolsCall("list_issues")
                .withArguments(Map.of("parent_id", parentId))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    assertThat(idsOfRowsWithTitle(response, "Order Stability "))
                            .containsExactlyElementsOf(oldestFirst);
                })
                .send()
                .thenAssertResults();

        // The detail payload and the resource embed the children inside one JSON
        // document: each child's title must be present (a dropped child would
        // otherwise slip an indexOf of -1 past an ordering comparison) and the
        // positions must read oldest-first.
        List<String> titlesOldestFirst = List.of("Order Stability Oldest",
                "Order Stability Middle", "Order Stability Newest");

        client.when()
                .toolsCall("get_issue")
                .withArguments(Map.of("id", parentId))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    String detail = response.firstContent().asText().text();
                    assertThat(positionsIn(detail, titlesOldestFirst))
                            .allMatch(position -> position >= 0)
                            .isSorted();
                })
                .send()
                .thenAssertResults();

        client.when()
                .resourcesRead("issue:///" + parentId, response -> {
                    String detail = response.firstContents().asText().text();
                    assertThat(positionsIn(detail, titlesOldestFirst))
                            .allMatch(position -> position >= 0)
                            .isSorted();
                })
                .thenAssertResults();
    }

    /**
     * list_issues expresses all three assignee-presence modes: true = claimed by
     * anyone, false = unclaimed, absent = no assignee filter — the gap where an
     * agent could say "unassigned" but not "assigned" is closed.
     */
    @Test
    public void testListIssuesHasAssigneeModes() {
        createTestIssueEntity("Presence Assigned", "", "open", "agent-x");
        createTestIssueEntity("Presence Unassigned", "", "open", null);

        client.when()
                .toolsCall("list_issues")
                .withArguments(Map.of("has_assignee", true))
                .withAssert(response -> {
                    String text = allRowsText(response);
                    assertThat(text).contains("Presence Assigned");
                    assertThat(text).doesNotContain("Presence Unassigned");
                })
                .send()
                .thenAssertResults();

        client.when()
                .toolsCall("list_issues")
                .withArguments(Map.of("has_assignee", false))
                .withAssert(response -> {
                    String text = allRowsText(response);
                    assertThat(text).contains("Presence Unassigned");
                    assertThat(text).doesNotContain("Presence Assigned");
                })
                .send()
                .thenAssertResults();

        client.when()
                .toolsCall("list_issues")
                .withArguments(Map.of())
                .withAssert(response -> {
                    String text = allRowsText(response);
                    assertThat(text).contains("Presence Assigned");
                    assertThat(text).contains("Presence Unassigned");
                })
                .send()
                .thenAssertResults();
    }

    /**
     * A non-empty assignee takes precedence over the presence flag, the rule the
     * REST surface serves — asking for agent-x while also saying has_assignee=false
     * is a contradiction the named assignee wins.
     */
    @Test
    public void testListIssuesAssigneeTakesPrecedenceOverHasAssignee() {
        createTestIssueEntity("Precedence Assigned", "", "open", "agent-x");
        createTestIssueEntity("Precedence Unassigned", "", "open", null);

        client.when()
                .toolsCall("list_issues")
                .withArguments(Map.of("assignee", "agent-x", "has_assignee", false))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    String text = allRowsText(response);
                    assertThat(text).contains("Precedence Assigned");
                    assertThat(text).doesNotContain("Precedence Unassigned");
                })
                .send()
                .thenAssertResults();
    }

    /**
     * The empty-string assignee keeps meaning unassigned while no flag travels
     * with it, the one expressible mode before the flag existed — but an
     * explicit has_assignee wins over the empty string, which is not an assignee
     * name at all.
     */
    @Test
    public void testListIssuesEmptyAssigneeStillMeansUnassigned() {
        createTestIssueEntity("Empty Assignee Assigned", "", "open", "agent-x");
        createTestIssueEntity("Empty Assignee Unassigned", "", "open", null);

        client.when()
                .toolsCall("list_issues")
                .withArguments(Map.of("assignee", ""))
                .withAssert(response -> {
                    String text = allRowsText(response);
                    assertThat(text).contains("Empty Assignee Unassigned");
                    assertThat(text).doesNotContain("Empty Assignee Assigned");
                })
                .send()
                .thenAssertResults();

        client.when()
                .toolsCall("list_issues")
                .withArguments(Map.of("assignee", "", "has_assignee", true))
                .withAssert(response -> {
                    String text = allRowsText(response);
                    assertThat(text).contains("Empty Assignee Assigned");
                    assertThat(text).doesNotContain("Empty Assignee Unassigned");
                })
                .send()
                .thenAssertResults();
    }

    /**
     * Agents read tool descriptions on connect, so the ordering defaults and the
     * has_assignee argument are stated where they will actually be read — tools
     * and the issue resource alike, the resource being the one surface no other
     * assertion pins.
     */
    @Test
    public void testOrderingAndAssigneeAreDocumentedToolDescriptions() {
        client.when()
                .toolsList(page -> {
                    assertThat(page.findByName("list_frontier").description())
                            .contains("oldest").contains("createdAt").contains("unclaimed");
                    assertThat(page.findByName("list_issues").description())
                            .contains("has_assignee").contains("oldest");
                    assertThat(page.findByName("list_children").description())
                            .contains("oldest");
                    assertThat(page.findByName("get_issue").description())
                            .contains("oldest");
                })
                .thenAssertResults();

        client.when()
                .resourcesTemplatesList(page -> {
                    assertThat(page.findByUriTemplate("issue:///{id}").description())
                            .contains("oldest").contains("createdAt");
                })
                .thenAssertResults();
    }

    // ── 15. claim_issue ──────────────────────────────────────────────────────

    @Test
    public void testClaimIssue() {
        Long id = createTestIssueEntity("Claimable Issue");

        client.when()
                .toolsCall("claim_issue")
                .withArguments(Map.of(
                        "id", id,
                        "agent_name", "agent-bob"
                ))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    String text = response.firstContent().asText().text();
                    assertThat(text).contains("agent-bob");
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testClaimIssueAlreadyClaimed() {
        Long id = createTestIssueEntity("Already Claimed Issue", "", "open", "agent-alpha");

        // Try to claim with a different agent
        client.when()
                .toolsCall("claim_issue")
                .withArguments(Map.of(
                        "id", id,
                        "agent_name", "agent-beta"
                ))
                .withErrorAssert(error -> {
                    assertThat(error.code()).isEqualTo(-32602); // INVALID_PARAMS
                    assertThat(error.message()).contains("already claimed");
                })
                .send()
                .thenAssertResults();
    }

    // ── 16. comment ─────────────────────────────────────────────────────────

    @Test
    public void testComment() {
        Long id = createTestIssueEntity("Issue For Comment");

        client.when()
                .toolsCall("comment")
                .withArguments(Map.of(
                        "id", id,
                        "author", "agent-bob",
                        "body", "This is a comment via MCP"
                ))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    String text = response.firstContent().asText().text();
                    assertThat(text).contains("agent-bob");
                    assertThat(text).contains("This is a comment via MCP");
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testCommentEmptyBody() {
        Long id = createTestIssueEntity("Issue For Empty Comment Test");

        client.when()
                .toolsCall("comment")
                .withArguments(Map.of(
                        "id", id,
                        "author", "agent-bob",
                        "body", ""
                ))
                .withErrorAssert(error -> {
                    assertThat(error.code()).isEqualTo(-32602); // INVALID_PARAMS
                    assertThat(error.message().toLowerCase()).contains("body");
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testCommentBlankBody() {
        Long id = createTestIssueEntity("Issue For Blank Comment Test");

        client.when()
                .toolsCall("comment")
                .withArguments(Map.of(
                        "id", id,
                        "author", "agent-bob",
                        "body", "   "
                ))
                .withErrorAssert(error -> {
                    assertThat(error.code()).isEqualTo(-32602); // INVALID_PARAMS
                    assertThat(error.message().toLowerCase()).contains("body");
                })
                .send()
                .thenAssertResults();
    }

    // ── Resource: issue:///{id} ─────────────────────────────────────────────

    @Test
    public void testReadIssueResource() {
        Long id = createTestIssueEntity("Resource Test Issue");

        client.when()
                .resourcesRead("issue:///" + id, response -> {
                    assertThat(response.contents()).isNotEmpty();
                    String text = response.firstContents().asText().text();
                    assertThat(text).contains("Resource Test Issue");
                })
                .thenAssertResults();
    }

    @Test
    public void testReadIssueResourceEmbedsChildren() {
        Long parentId = createTestIssueEntity("Resource Parent Issue");
        client.when()
                .toolsCall("create_issue")
                .withArguments(Map.of("title", "Resource Child Issue", "parent_id", parentId))
                .withAssert(response -> assertThat(response.isError()).isFalse())
                .send()
                .thenAssertResults();

        client.when()
                .resourcesRead("issue:///" + parentId, response -> {
                    assertThat(response.contents()).isNotEmpty();
                    String text = response.firstContents().asText().text();
                    assertThat(text).contains("Resource Child Issue");
                })
                .thenAssertResults();
    }

    @Test
    public void testReadIssueResourceNotFound() {
        client.when()
                .resourcesRead("issue:///999999")
                .withErrorAssert(error -> {
                    assertThat(error.code()).isEqualTo(-32002); // RESOURCE_NOT_FOUND
                })
                .send()
                .thenAssertResults();
    }

    // ── End-to-end smoke test ──────────────────────────────────────────────

    @Test
    public void testEndToEndSmokeTest() {
        // Create an issue
        client.when()
                .toolsCall("create_issue")
                .withArguments(Map.of(
                        "title", "E2E Smoke Test Issue",
                        "description", "Testing full workflow"
                ))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                })
                .send()
                .thenAssertResults();

        // List issues — should contain our issue
        client.when()
                .toolsCall("list_issues")
                .withArguments(Map.of("search", "E2E Smoke Test"))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    String text = response.firstContent().asText().text();
                    assertThat(text).contains("E2E Smoke Test");
                })
                .send()
                .thenAssertResults();

        // Claim the issue
        Long issueId = findIssueByTitle("E2E Smoke Test Issue");

        // Add a comment
        client.when()
                .toolsCall("comment")
                .withArguments(Map.of(
                        "id", issueId,
                        "author", "e2e-agent",
                        "body", "E2E test comment"
                ))
                .withAssert(response -> assertThat(response.isError()).isFalse())
                .send()
                .thenAssertResults();

        // Close the issue
        client.when()
                .toolsCall("close_issue")
                .withArguments(Map.of(
                        "id", issueId,
                        "comment", "Closing E2E test issue",
                        "author", "e2e-agent"
                ))
                .withAssert(response -> {
                    assertThat(response.isError()).isFalse();
                    String text = response.firstContent().asText().text();
                    assertThat(text).contains("closed");
                })
                .send()
                .thenAssertResults();
    }

    // ── Helper to find issue ID by title ───────────────────────────────────

    private Long findIssueByTitle(String title) {
        return issueRepository.find("title", title).firstResult().id;
    }
}
