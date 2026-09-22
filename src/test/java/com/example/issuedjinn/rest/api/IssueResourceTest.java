package com.example.issuedjinn.rest.api;

import com.example.issuedjinn.domain.entity.Issue;
import com.example.issuedjinn.domain.repository.CommentRepository;
import com.example.issuedjinn.domain.repository.IssueRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;
import io.restassured.response.Response;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

/**
 * Integration tests for IssueResource using @QuarkusTest + RestAssured with AssertJ assertions.
 */
@QuarkusTest
public class IssueResourceTest {

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


    private Long createIssueViaApi(String title, String description) {
        String requestBody = String.format("""
                {
                    "title": "%s",
                    "description": "%s"
                }
                """, title, description != null ? description : "");

        Response response = given()
                .contentType("application/json")
                .body(requestBody)
                .when().post("/api/issues")
                .then()
                .statusCode(201)
                .extract().response();

        return response.jsonPath().getLong("id");
    }

    private Long createIssueWithAssigneeViaApi(String title, String assignee) {
        String requestBody = String.format("""
                {
                    "title": "%s",
                    "description": ""
                }
                """, title);

        Response response = given()
                .contentType("application/json")
                .body(requestBody)
                .when().post("/api/issues")
                .then()
                .statusCode(201)
                .extract().response();

        Long issueId = response.jsonPath().getLong("id");
        
        // Assign the issue
        String assignRequestBody = String.format("""
                {
                    "assignee": "%s"
                }
                """, assignee);
        given()
                .contentType("application/json")
                .body(assignRequestBody)
                .when().patch("/api/issues/" + issueId)
                .then()
                .statusCode(200);

        return issueId;
    }

    private void closeIssueViaApi(Long issueId) {
        String requestBody = """
                {
                    "comment": "Closing comment",
                    "author": "test-agent"
                }
                """;
        given()
                .contentType("application/json")
                .body(requestBody)
                .when().post("/api/issues/" + issueId + "/close")
                .then()
                .statusCode(200);
    }

    private void touchIssueViaApi(Long issueId) {
        String requestBody = """
                {
                    "description": "touched"
                }
                """;
        given()
                .contentType("application/json")
                .body(requestBody)
                .when().patch("/api/issues/" + issueId)
                .then()
                .statusCode(200);
    }

    @Test
    public void testHealthEndpoint() {
        Response response = given()
                .when().get("/api/health")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .extract().response();

        assertThat(response.jsonPath().getString("status")).isEqualTo("ok");
    }

    @Test
    public void testCreateIssue() {
        String requestBody = """
                {
                    "title": "Test Issue",
                    "description": "Test description"
                }
                """;

        Response response = given()
                .contentType("application/json")
                .body(requestBody)
                .when().post("/api/issues")
                .then()
                .statusCode(201)
                .contentType("application/json")
                .extract().response();

        Long issueId = response.jsonPath().getLong("id");
        assertThat(issueId).isNotNull();
        assertThat(response.jsonPath().getString("title")).isEqualTo("Test Issue");
        assertThat(response.jsonPath().getString("description")).isEqualTo("Test description");
        assertThat(response.jsonPath().getString("status")).isEqualTo("open");
    }

    @Test
    public void testCreateIssueWithParentAndLabels() {
        Long parentId = createIssueViaApi("Parent Issue", "");

        String requestBody = String.format("""
                {
                    "title": "Child Issue",
                    "description": "Test description",
                    "parentId": %d,
                    "labels": ["task", "ready-for-agent"]
                }
                """, parentId);

        Response response = given()
                .contentType("application/json")
                .body(requestBody)
                .when().post("/api/issues")
                .then()
                .statusCode(201)
                .contentType("application/json")
                .extract().response();

        assertThat(response.jsonPath().getLong("parentId")).isEqualTo(parentId);
        // Labels are a Set server-side, so their order is not part of the contract.
        assertThat(response.jsonPath().<String>getList("labels"))
                .containsExactlyInAnyOrder("task", "ready-for-agent");
    }

    @Test
    public void testCreateIssueWithUnknownParent() {
        String requestBody = """
                {
                    "title": "Orphan Issue",
                    "description": "",
                    "parentId": 99999
                }
                """;

        Response response = given()
                .contentType("application/json")
                .body(requestBody)
                .when().post("/api/issues")
                .then()
                .statusCode(404)
                .contentType("application/json")
                .extract().response();

        assertThat(response.jsonPath().getString("error.code")).isEqualTo("ISSUE_NOT_FOUND");
    }

    @Test
    public void testCreateIssueUnderChildRejected() {
        Long parentId = createIssueViaApi("Parent Issue", "");
        Long childId = createIssueViaApi("Child Issue", "");
        patchParent(childId, parentId);

        String requestBody = String.format("""
                {
                    "title": "Grandchild Issue",
                    "description": "",
                    "parentId": %d
                }
                """, childId);

        Response response = given()
                .contentType("application/json")
                .body(requestBody)
                .when().post("/api/issues")
                .then()
                .statusCode(409)
                .contentType("application/json")
                .extract().response();

        assertThat(response.jsonPath().getString("error.code")).isEqualTo("HIERARCHY_DEPTH_EXCEEDED");
    }

    @Test
    public void testCreateIssueMissingTitle() {
        String requestBody = """
                {
                    "title": "",
                    "description": "Test description"
                }
                """;

        Response response = given()
                .contentType("application/json")
                .body(requestBody)
                .when().post("/api/issues")
                .then()
                .statusCode(400)
                .contentType("application/json")
                .extract().response();

        assertThat(response.jsonPath().getString("error.code")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    public void testCreateIssueNullTitle() {
        String requestBody = """
                {
                    "description": "Test description"
                }
                """;

        Response response = given()
                .contentType("application/json")
                .body(requestBody)
                .when().post("/api/issues")
                .then()
                .statusCode(400)
                .contentType("application/json")
                .extract().response();

        assertThat(response.jsonPath().getString("error.code")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    public void testGetIssueById() {
        Long issueId = createIssueViaApi("Test Issue", "Test description");

        Response response = given()
                .when().get("/api/issues/" + issueId)
                .then()
                .statusCode(200)
                .contentType("application/json")
                .extract().response();

        assertThat(response.jsonPath().getLong("id")).isEqualTo(issueId);
        assertThat(response.jsonPath().getString("title")).isEqualTo("Test Issue");
        assertThat(response.jsonPath().getInt("commentCount")).isEqualTo(0);
    }

    @Test
    public void testGetIssueNotFound() {
        Response response = given()
                .when().get("/api/issues/99999")
                .then()
                .statusCode(404)
                .contentType("application/json")
                .extract().response();

        assertThat(response.jsonPath().getString("error.code")).isEqualTo("ISSUE_NOT_FOUND");
    }

    @Test
    public void testGetIssueEmbedsChildren() {
        Long parentId = createIssueViaApi("Parent issue", "");
        Long openChild = createIssueViaApi("Open child", "");
        Long closedChild = createIssueViaApi("Closed child", "");
        patchParent(openChild, parentId);
        patchParent(closedChild, parentId);
        closeIssueViaApi(closedChild);
        // A later PATCH makes the closed child unambiguously the most recently
        // updated — an updatedAt order would put it first, but children follow
        // creation instead.
        touchIssueViaApi(closedChild);

        Response response = given()
                .when().get("/api/issues/" + parentId)
                .then()
                .statusCode(200)
                .contentType("application/json")
                .extract().response();

        // Children ride along in the light list shape, every status included,
        // oldest-created-first
        assertThat(response.jsonPath().getList("children.id", Long.class))
                .containsExactly(openChild, closedChild);
        assertThat(response.jsonPath().getString("children[0].title")).isEqualTo("Open child");
        assertThat(response.jsonPath().getString("children[0].status")).isEqualTo("open");
        assertThat(response.jsonPath().getString("children[1].status")).isEqualTo("closed");
        // The row is the light list shape: parent, labels and assignee ride along too
        assertThat(response.jsonPath().getLong("children[0].parentId")).isEqualTo(parentId);
        assertThat(response.jsonPath().getList("children[0].labels", String.class)).isEmpty();
        assertThat(response.jsonPath().getString("children[0].assignee")).isNull();
        assertThat(response.jsonPath().getString("children[0].updatedAt")).isNotNull();
    }

    @Test
    public void testGetIssueWithoutChildrenEmbedsEmptyChildren() {
        Long issueId = createIssueViaApi("Leaf issue", "");

        Response response = given()
                .when().get("/api/issues/" + issueId)
                .then()
                .statusCode(200)
                .contentType("application/json")
                .extract().response();

        assertThat(response.jsonPath().getList("children.id", Long.class)).isEmpty();
    }

    @Test
    public void testGetIssueChildrenRowsCarryNoChildCounts() {
        // Child counts belong to the list's root rows; a detail payload's child
        // rows are the lightest shape and carry nothing of their own.
        Long parentId = createIssueViaApi("Parent issue", "");
        Long childId = createIssueViaApi("Child issue", "");
        patchParent(childId, parentId);

        Response response = given()
                .when().get("/api/issues/" + parentId)
                .then()
                .statusCode(200)
                .contentType("application/json")
                .extract().response();

        assertThat(response.getBody().asString()).doesNotContain("childCounts");
    }

    @Test
    public void testGetIssueEmbedsLegacyChildren() {
        // A chain deeper than the cap predates the rule: the middle issue's detail
        // page still shows its grandchild, even though nothing new can hang under it
        List<Long> chain = createLegacyChain("Legacy issue", 3);
        Long middleId = chain.get(1);
        Long grandchildId = chain.get(2);

        Response response = given()
                .when().get("/api/issues/" + middleId)
                .then()
                .statusCode(200)
                .extract().response();

        assertThat(response.jsonPath().getList("children.id", Long.class))
                .containsExactly(grandchildId);
    }

    @Test
    public void testListIssues() {
        createIssueViaApi("Issue 1", "");
        createIssueViaApi("Issue 2", "");

        Response response = given()
                .when().get("/api/issues")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .extract().response();

        // Check that the response contains the issues array with 2 items
        String responseBody = response.getBody().asString();
        assertThat(responseBody).contains("\"issues\":[");
        assertThat(responseBody).contains("\"title\":\"Issue 1\"");
        assertThat(responseBody).contains("\"title\":\"Issue 2\"");
    }

    @Test
    public void testListIssuesWithStatusFilter() {
        Long issueId1 = createIssueViaApi("Open Issue", "");
        
        // Close the issue via API
        String closeBody = """
                {
                    "comment": "Closing for test",
                    "author": "test-agent"
                }
                """;
        given()
                .contentType("application/json")
                .body(closeBody)
                .when().post("/api/issues/" + issueId1 + "/close")
                .then()
                .statusCode(200);

        Response response = given()
                .when().queryParam("status", "closed")
                .get("/api/issues")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .extract().response();

        String responseBody = response.getBody().asString();
        assertThat(responseBody).contains("\"status\":\"closed\"");
        assertThat(responseBody).contains("\"title\":\"Open Issue\"");
    }

    @Test
    public void testUpdateIssue() {
        Long issueId = createIssueViaApi("Original Title", "Original description");

        String requestBody = """
                {
                    "title": "Updated Title",
                    "description": "Updated description"
                }
                """;

        Response response = given()
                .contentType("application/json")
                .body(requestBody)
                .when().patch("/api/issues/" + issueId)
                .then()
                .statusCode(200)
                .contentType("application/json")
                .extract().response();

        assertThat(response.jsonPath().getString("title")).isEqualTo("Updated Title");
        assertThat(response.jsonPath().getString("description")).isEqualTo("Updated description");
    }

    @Test
    public void testPatchIssueUnknownFieldRejected() {
        // Regression test: a client-side rename (parent_id instead of parentId —
        // the MCP argument name) must fail loudly instead of silently dropping the value.
        Long issueId = createIssueViaApi("Issue For Unknown Field", "");

        String requestBody = String.format("""
                {
                    "parent_id": 1
                }
                """);

        Response response = given()
                .contentType("application/json")
                .body(requestBody)
                .when().patch("/api/issues/" + issueId)
                .then()
                .statusCode(400)
                .extract().response();

        assertThat(response.jsonPath().getString("error.code")).isEqualTo("UNKNOWN_FIELD");
        assertThat(response.jsonPath().getString("error.message")).contains("parent_id");
    }

    @Test
    public void testCreateIssueUnknownFieldRejected() {
        String requestBody = """
                {
                    "title": "Issue With Unknown Field",
                    "parent_id": 1
                }
                """;

        Response response = given()
                .contentType("application/json")
                .body(requestBody)
                .when().post("/api/issues")
                .then()
                .statusCode(400)
                .extract().response();

        assertThat(response.jsonPath().getString("error.code")).isEqualTo("UNKNOWN_FIELD");
        assertThat(response.jsonPath().getString("error.message")).contains("parent_id");
    }

    @Test
    public void testPatchIssueReparent() {
        Long parentId = createIssueViaApi("New Parent", "");
        Long childId = createIssueViaApi("Child", "");

        String requestBody = String.format("""
                {
                    "parentId": %d
                }
                """, parentId);

        Response response = given()
                .contentType("application/json")
                .body(requestBody)
                .when().patch("/api/issues/" + childId)
                .then()
                .statusCode(200)
                .contentType("application/json")
                .extract().response();

        assertThat(response.jsonPath().getLong("parentId")).isEqualTo(parentId);
    }

    @Test
    public void testPatchIssueWithoutParentIdKeepsParent() {
        Long parentId = createIssueViaApi("Parent", "");
        Long childId = createIssueViaApi("Child", "");

        String requestBody = String.format("""
                {
                    "parentId": %d
                }
                """, parentId);
        given()
                .contentType("application/json")
                .body(requestBody)
                .when().patch("/api/issues/" + childId)
                .then()
                .statusCode(200);

        // A PATCH that omits parentId must not touch the parent
        String renameBody = """
                {
                    "title": "Renamed Child"
                }
                """;
        Response response = given()
                .contentType("application/json")
                .body(renameBody)
                .when().patch("/api/issues/" + childId)
                .then()
                .statusCode(200)
                .extract().response();

        assertThat(response.jsonPath().getLong("parentId")).isEqualTo(parentId);
    }

    @Test
    public void testPatchIssueReparentToClosedParent() {
        Long parentId = createIssueViaApi("Closed Parent", "");
        String closeBody = """
                {
                    "comment": "closing parent",
                    "author": "test"
                }
                """;
        given()
                .contentType("application/json")
                .body(closeBody)
                .when().post("/api/issues/" + parentId + "/close")
                .then()
                .statusCode(200);
        Long childId = createIssueViaApi("Child", "");

        String requestBody = String.format("""
                {
                    "parentId": %d
                }
                """, parentId);

        given()
                .contentType("application/json")
                .body(requestBody)
                .when().patch("/api/issues/" + childId)
                .then()
                .statusCode(200);
    }

    @Test
    public void testPatchIssueReparentUnknownParent() {
        Long childId = createIssueViaApi("Child", "");

        String requestBody = """
                {
                    "parentId": 999999
                }
                """;

        Response response = given()
                .contentType("application/json")
                .body(requestBody)
                .when().patch("/api/issues/" + childId)
                .then()
                .statusCode(404)
                .extract().response();

        assertThat(response.jsonPath().getString("error.code")).isEqualTo("ISSUE_NOT_FOUND");
    }

    @Test
    public void testPatchIssueReparentSelfRejected() {
        Long issueId = createIssueViaApi("Self Parent", "");

        String requestBody = String.format("""
                {
                    "parentId": %d
                }
                """, issueId);

        Response response = given()
                .contentType("application/json")
                .body(requestBody)
                .when().patch("/api/issues/" + issueId)
                .then()
                .statusCode(409)
                .extract().response();

        assertThat(response.jsonPath().getString("error.code")).isEqualTo("CYCLE_DETECTED");
    }

    @Test
    public void testPatchIssueReparentCycleRejected() {
        // The chain root -> middle -> deepest is written directly: growing it through
        // the API is now rejected by the depth cap, so cycle prevention is exercised
        // on the shape a legacy chain has. Moving the root under the deepest
        // issue closes the ring root -> middle -> deepest -> root.
        List<Long> chain = createLegacyChain("Issue", 3);
        Long rootId = chain.get(0);
        Long deepestId = chain.get(2);

        Response response = given()
                .contentType("application/json")
                .body(String.format("{\"parentId\": %d}", deepestId))
                .when().patch("/api/issues/" + rootId)
                .then()
                .statusCode(409)
                .extract().response();

        assertThat(response.jsonPath().getString("error.code")).isEqualTo("CYCLE_DETECTED");
    }

    @Test
    public void testPatchIssueReparentUnderChildRejected() {
        Long parentId = createIssueViaApi("Parent Issue", "");
        Long childId = createIssueViaApi("Child Issue", "");
        patchParent(childId, parentId);
        Long otherId = createIssueViaApi("Other Issue", "");

        Response response = given()
                .contentType("application/json")
                .body(String.format("{\"parentId\": %d}", childId))
                .when().patch("/api/issues/" + otherId)
                .then()
                .statusCode(409)
                .extract().response();

        assertThat(response.jsonPath().getString("error.code")).isEqualTo("HIERARCHY_DEPTH_EXCEEDED");
    }

    @Test
    public void testPatchIssueReparentUnderChildRejectsWholeUpdate() {
        // Atomicity: a rejected parent change must also reject the other fields in
        // the same PATCH — the title must survive unchanged.
        Long parentId = createIssueViaApi("Parent Issue", "");
        Long childId = createIssueViaApi("Child Issue", "");
        patchParent(childId, parentId);
        Long otherId = createIssueViaApi("Original Title", "");

        given()
                .contentType("application/json")
                .body(String.format("""
                        {
                            "title": "Renamed By Rejected Patch",
                            "parentId": %d
                        }
                        """, childId))
                .when().patch("/api/issues/" + otherId)
                .then()
                .statusCode(409);

        Response after = given()
                .when().get("/api/issues/" + otherId)
                .then()
                .statusCode(200)
                .extract().response();

        assertThat(after.jsonPath().getString("title")).isEqualTo("Original Title");
    }

    @Test
    public void testLegacyDeepChainStillRetrievable() {
        // A chain deeper than the cap predates the rule: never migrated, still
        // readable everywhere it was readable before — the detail endpoint and
        // the parent-filtered list alike.
        List<Long> chain = createLegacyChain("Legacy issue", 3);
        Long rootId = chain.get(0);
        Long middleId = chain.get(1);
        Long grandchildId = chain.get(2);

        Response detail = given()
                .when().get("/api/issues/" + grandchildId)
                .then()
                .statusCode(200)
                .extract().response();

        assertThat(detail.jsonPath().getLong("parentId")).isEqualTo(middleId);

        Response list = given()
                .when().queryParam("parent", rootId)
                .get("/api/issues")
                .then()
                .statusCode(200)
                .extract().response();

        assertThat(list.jsonPath().getList("issues.id", Long.class))
                .containsExactlyInAnyOrder(middleId);
    }

    @Test
    public void testPatchIssueLegacyIssueCanKeepItsParent() {
        // Re-setting the parent a legacy issue already has changes no depth,
        // so the PATCH still succeeds — the cap rejects growth, not pre-existing depth.
        List<Long> chain = createLegacyChain("Legacy issue", 3);
        Long grandchildId = chain.get(2);
        Long existingParentId = chain.get(1);

        given()
                .contentType("application/json")
                .body(String.format("{\"parentId\": %d}", existingParentId))
                .when().patch("/api/issues/" + grandchildId)
                .then()
                .statusCode(200);
    }

    /**
     * Persist a chain of the given length by writing the parent links directly,
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

    @Test
    public void testPatchIssueReparentRejectsWholeUpdate() {
        // Atomicity: a rejected parent change must also reject the other fields in
        // the same PATCH — the title must survive unchanged.
        Long childId = createIssueViaApi("Original Title", "");

        String requestBody = """
                {
                    "title": "Renamed By Rejected Patch",
                    "parentId": 999999
                }
                """;

        given()
                .contentType("application/json")
                .body(requestBody)
                .when().patch("/api/issues/" + childId)
                .then()
                .statusCode(404)
                .extract().response();

        Response after = given()
                .when().get("/api/issues/" + childId)
                .then()
                .statusCode(200)
                .extract().response();

        assertThat(after.jsonPath().getString("title")).isEqualTo("Original Title");
    }

    @Test
    public void testPatchIssueUnparent() {
        Long parentId = createIssueViaApi("Parent", "");
        Long childId = createIssueViaApi("Child", "");
        patchParent(childId, parentId);

        Response response = given()
                .contentType("application/json")
                .body("{\"parentId\": null}")
                .when().patch("/api/issues/" + childId)
                .then()
                .statusCode(200)
                .contentType("application/json")
                .extract().response();

        assertThat(response.jsonPath().getString("parentId")).isNull();
    }

    @Test
    public void testPatchIssueUnparentWithoutParent() {
        Long issueId = createIssueViaApi("Root Issue", "");

        given()
                .contentType("application/json")
                .body("{\"parentId\": null}")
                .when().patch("/api/issues/" + issueId)
                .then()
                .statusCode(200);
    }

    @Test
    public void testPatchIssueUnparentNotFound() {
        given()
                .contentType("application/json")
                .body("{\"parentId\": null}")
                .when().patch("/api/issues/999999")
                .then()
                .statusCode(404);
    }

    private void patchParent(Long childId, Long parentId) {
        String requestBody = String.format("""
                {
                    "parentId": %d
                }
                """, parentId);
        given()
                .contentType("application/json")
                .body(requestBody)
                .when().patch("/api/issues/" + childId)
                .then()
                .statusCode(200);
    }

    @Test
    public void testCloseIssue() {
        Long issueId = createIssueViaApi("To Close", "");

        String requestBody = """
                {
                    "comment": "Closing comment",
                    "author": "test-agent"
                }
                """;

        Response response = given()
                .contentType("application/json")
                .body(requestBody)
                .when().post("/api/issues/" + issueId + "/close")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .extract().response();

        assertThat(response.jsonPath().getString("status")).isEqualTo("closed");
        assertThat(response.jsonPath().getInt("commentCount")).isEqualTo(1);
    }

    @Test
    public void testCloseIssueMissingComment() {
        Long issueId = createIssueViaApi("To Close", "");

        String requestBody = """
                {
                    "comment": "",
                    "author": "test-agent"
                }
                """;

        Response response = given()
                .contentType("application/json")
                .body(requestBody)
                .when().post("/api/issues/" + issueId + "/close")
                .then()
                .statusCode(400)
                .contentType("application/json")
                .extract().response();

        assertThat(response.jsonPath().getString("error.code")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    public void testCloseIssueMissingAuthor() {
        Long issueId = createIssueViaApi("To Close", "");

        String requestBody = """
                {
                    "comment": "Closing comment",
                    "author": ""
                }
                """;

        Response response = given()
                .contentType("application/json")
                .body(requestBody)
                .when().post("/api/issues/" + issueId + "/close")
                .then()
                .statusCode(400)
                .contentType("application/json")
                .extract().response();

        assertThat(response.jsonPath().getString("error.code")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    public void testReopenIssue() {
        Long issueId = createIssueViaApi("To Reopen", "");
        
        // Close it first
        given()
                .contentType("application/json")
                .body("{\"comment\": \"Closing\", \"author\": \"test-agent\"}")
                .when().post("/api/issues/" + issueId + "/close")
                .then()
                .statusCode(200);

        String requestBody = """
                {
                    "comment": "Reopening comment",
                    "author": "test-agent"
                }
                """;

        Response response = given()
                .contentType("application/json")
                .body(requestBody)
                .when().post("/api/issues/" + issueId + "/reopen")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .extract().response();

        assertThat(response.jsonPath().getString("status")).isEqualTo("open");
        assertThat(response.jsonPath().getInt("commentCount")).isEqualTo(2);
    }

    @Test
    public void testReopenIssueMissingComment() {
        Long issueId = createIssueViaApi("To Reopen", "");

        // Close it first
        given()
                .contentType("application/json")
                .body("{\"comment\": \"Closing\", \"author\": \"test-agent\"}")
                .when().post("/api/issues/" + issueId + "/close")
                .then()
                .statusCode(200);

        String requestBody = """
                {
                    "comment": "",
                    "author": "test-agent"
                }
                """;

        Response response = given()
                .contentType("application/json")
                .body(requestBody)
                .when().post("/api/issues/" + issueId + "/reopen")
                .then()
                .statusCode(400)
                .contentType("application/json")
                .extract().response();

        assertThat(response.jsonPath().getString("error.code")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    public void testUnassignIssue() {
        Long issueId = createIssueViaApi("Assigned Issue", "");
        
        // Assign it first
        String assignBody = """
                {
                    "assignee": "test-user"
                }
                """;
        given()
                .contentType("application/json")
                .body(assignBody)
                .when().patch("/api/issues/" + issueId)
                .then()
                .statusCode(200);

        Response response = given()
                .when().post("/api/issues/" + issueId + "/unassign")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .extract().response();

        // Check that assignee is null in the response
        String responseBody = response.getBody().asString();
        assertThat(responseBody).contains("\"assignee\":null");
    }

    @Test
    public void testAddDependency() {
        Long dependentId = createIssueViaApi("Dependent", "");
        Long dependencyId = createIssueViaApi("Dependency", "");

        String requestBody = String.format("""
                {
                    "dependency_id": %d
                }
                """, dependencyId);

        Response response = given()
                .contentType("application/json")
                .body(requestBody)
                .when().post("/api/issues/" + dependentId + "/dependencies")
                .then()
                .statusCode(204)
                .extract().response();

        assertThat(response.getStatusCode()).isEqualTo(204);
    }

    @Test
    public void testAddDependencyMissingDependencyId() {
        Long dependentId = createIssueViaApi("Dependent", "");

        String requestBody = """
                {
                }
                """;

        Response response = given()
                .contentType("application/json")
                .body(requestBody)
                .when().post("/api/issues/" + dependentId + "/dependencies")
                .then()
                .statusCode(400)
                .contentType("application/json")
                .extract().response();

        assertThat(response.jsonPath().getString("error.code")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    public void testAddDependencyCycleDetection() {
        Long issueA = createIssueViaApi("Issue A", "");
        Long issueB = createIssueViaApi("Issue B", "");

        // Add dependency: A depends on B
        String requestBodyAB = String.format("""
                {
                    "dependency_id": %d
                }
                """, issueB);
        given()
                .contentType("application/json")
                .body(requestBodyAB)
                .when().post("/api/issues/" + issueA + "/dependencies")
                .then()
                .statusCode(204);

        // Try to add dependency: B depends on A (would create cycle)
        String requestBodyBA = String.format("""
                {
                    "dependency_id": %d
                }
                """, issueA);
        Response response = given()
                .contentType("application/json")
                .body(requestBodyBA)
                .when().post("/api/issues/" + issueB + "/dependencies")
                .then()
                .statusCode(409)
                .contentType("application/json")
                .extract().response();

        assertThat(response.jsonPath().getString("error.code")).isEqualTo("CYCLE_DETECTED");
    }

    @Test
    public void testAddComment() {
        Long issueId = createIssueViaApi("Issue with comment", "");

        String requestBody = """
                {
                    "author": "test-user",
                    "body": "Test comment body"
                }
                """;

        Response response = given()
                .contentType("application/json")
                .body(requestBody)
                .when().post("/api/issues/" + issueId + "/comments")
                .then()
                .statusCode(201)
                .contentType("application/json")
                .extract().response();

        assertThat(response.jsonPath().getString("author")).isEqualTo("test-user");
        assertThat(response.jsonPath().getString("body")).isEqualTo("Test comment body");
    }

    @Test
    public void testAddCommentEmptyBody() {
        Long issueId = createIssueViaApi("Issue for empty body test", "");

        String requestBody = """
                {
                    "author": "test-user",
                    "body": ""
                }
                """;

        given()
                .contentType("application/json")
                .body(requestBody)
                .when().post("/api/issues/" + issueId + "/comments")
                .then()
                .statusCode(400);
    }

    @Test
    public void testAddCommentBlankBody() {
        Long issueId = createIssueViaApi("Issue for blank body test", "");

        String requestBody = """
                {
                    "author": "test-user",
                    "body": "   "
                }
                """;

        given()
                .contentType("application/json")
                .body(requestBody)
                .when().post("/api/issues/" + issueId + "/comments")
                .then()
                .statusCode(400);
    }

    @Test
    public void testAddCommentMissingBody() {
        Long issueId = createIssueViaApi("Issue for missing body test", "");

        String requestBody = """
                {
                    "author": "test-user"
                }
                """;

        given()
                .contentType("application/json")
                .body(requestBody)
                .when().post("/api/issues/" + issueId + "/comments")
                .then()
                .statusCode(400);
    }

    @Test
    public void testAddCommentMissingAuthor() {
        Long issueId = createIssueViaApi("Issue for missing author test", "");

        String requestBody = """
                {
                    "body": "Test comment body"
                }
                """;

        Response response = given()
                .contentType("application/json")
                .body(requestBody)
                .when().post("/api/issues/" + issueId + "/comments")
                .then()
                .statusCode(400)
                .contentType("application/json")
                .extract().response();

        assertThat(response.jsonPath().getString("error.code")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    public void testGetComments() {
        Long issueId = createIssueViaApi("Issue with comments", "");

        // Add comments
        String requestBody1 = """
                {
                    "author": "user1",
                    "body": "Comment 1"
                }
                """;
        given()
                .contentType("application/json")
                .body(requestBody1)
                .when().post("/api/issues/" + issueId + "/comments")
                .then()
                .statusCode(201);

        String requestBody2 = """
                {
                    "author": "user2",
                    "body": "Comment 2"
                }
                """;
        given()
                .contentType("application/json")
                .body(requestBody2)
                .when().post("/api/issues/" + issueId + "/comments")
                .then()
                .statusCode(201);

        Response response = given()
                .when().get("/api/issues/" + issueId + "/comments")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .extract().response();

        String responseBody = response.getBody().asString();
        assertThat(responseBody).contains("\"comments\":[");
        assertThat(responseBody).contains("\"author\":\"user1\"");
        assertThat(responseBody).contains("\"author\":\"user2\"");
    }

    @Test
    public void testGetLabels() {
        Long issueId = createIssueViaApi("Issue with labels", "");
        
        // Add labels via PATCH
        String labelsBody = """
                {
                    "labels": ["label1", "label2"]
                }
                """;
        given()
                .contentType("application/json")
                .body(labelsBody)
                .when().patch("/api/issues/" + issueId)
                .then()
                .statusCode(200);

        Response response = given()
                .when().get("/api/labels")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .extract().response();

        assertThat(response.jsonPath().getList("labels", String.class)).containsExactlyInAnyOrder("label1", "label2");
    }

    @Test
    public void testFrontierQuery() {
        // Create open, unblocked, unassigned issue (frontier)
        Long frontierIssueId = createIssueViaApi("Frontier Issue", "");

        // Create open, assigned issue
        Long assignedIssueId = createIssueViaApi("Assigned Issue", "");
        given()
                .contentType("application/json")
                .body("{\"assignee\": \"test-user\"}")
                .when().patch("/api/issues/" + assignedIssueId)
                .then()
                .statusCode(200);

        // Create open issue with dependency
        Long dependentId = createIssueViaApi("Dependent Issue", "");
        Long dependencyId = createIssueViaApi("Dependency Issue", "");
        
        // Add dependency via API
        String depBody = String.format("""
                {
                    "dependency_id": %d
                }
                """, dependencyId);
        given()
                .contentType("application/json")
                .body(depBody)
                .when().post("/api/issues/" + dependentId + "/dependencies")
                .then()
                .statusCode(204);

        // Query frontier: status=open&has_open_dependency=false&has_assignee=false
        Response response = given()
                .when().queryParam("status", "open")
                .queryParam("has_open_dependency", "false")
                .queryParam("has_assignee", "false")
                .get("/api/issues")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .extract().response();

        // Should include frontierIssue but not assignedIssue or dependentId
        String responseBody = response.getBody().asString();
        assertThat(responseBody).contains("\"title\":\"Frontier Issue\"");
        assertThat(responseBody).doesNotContain("\"title\":\"Assigned Issue\"");
        assertThat(responseBody).doesNotContain("\"title\":\"Dependent Issue\"");
    }
}
