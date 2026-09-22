package com.example.issuedjinn.domain.service;

import com.example.issuedjinn.domain.entity.Issue;
import com.example.issuedjinn.domain.repository.CommentRepository;
import com.example.issuedjinn.domain.repository.IssueRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static com.example.issuedjinn.domain.service.IssueService.CycleDetectedException;
import static com.example.issuedjinn.domain.service.IssueService.HierarchyDepthExceededException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for IssueService using @QuarkusTest + JUnit 5 with AssertJ assertions.
 */
@QuarkusTest
public class IssueServiceTest {

    @Inject
    IssueService issueService;

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

    private Issue createIssue(String title) {
        Issue issue = new Issue();
        issue.title = title;
        issue.persist();
        return issue;
    }

    @Test
    @Transactional
    public void testAddDependencySuccess() {
        // Create two issues
        Issue dependent = createIssue("Dependent issue");
        Issue dependency = createIssue("Dependency issue");

        // Add dependency relationship
        Issue result = issueService.addDependency(dependent.id, dependency.id);

        assertThat(result.id).isEqualTo(dependent.id);
        assertThat(result.dependencies).hasSize(1);
        assertThat(result.dependencies.iterator().next().id).isEqualTo(dependency.id);
    }

    @Test
    @Transactional
    public void testAddDependencyCycleDetection() {
        // Create three issues: A depends on B, B depends on C
        Issue issueA = createIssue("Issue A");
        Issue issueB = createIssue("Issue B");
        Issue issueC = createIssue("Issue C");

        // Add dependencies: A -> B, B -> C
        issueService.addDependency(issueA.id, issueB.id);
        issueService.addDependency(issueB.id, issueC.id);

        // Try to add dependency: C -> A (would create cycle A -> B -> C -> A)
        assertThatThrownBy(() -> {
            issueService.addDependency(issueC.id, issueA.id);
        })
        .isInstanceOf(CycleDetectedException.class)
        .hasMessage("Cannot add dependency: would create a cycle between issue " + issueC.id + " and issue " + issueA.id);
    }

    @Test
    @Transactional
    public void testAddDependencySelfReference() {
        // Create one issue
        Issue issue = createIssue("Issue A");

        // Try to add dependency: A -> A (self-reference)
        assertThatThrownBy(() -> {
            issueService.addDependency(issue.id, issue.id);
        })
        .isInstanceOf(CycleDetectedException.class)
        .hasMessage("Cannot add dependency: would create a cycle between issue " + issue.id + " and issue " + issue.id);
    }

    @Test
    @Transactional
    public void testRemoveDependency() {
        // Create two issues
        Issue dependent = createIssue("Dependent issue");
        Issue dependency = createIssue("Dependency issue");

        // Add dependency relationship
        issueService.addDependency(dependent.id, dependency.id);

        // Remove dependency relationship
        Issue result = issueService.removeDependency(dependent.id, dependency.id);

        assertThat(result.id).isEqualTo(dependent.id);
        assertThat(result.dependencies).hasSize(0);
    }

    @Test
    @Transactional
    public void testUpdateStatus() {
        // Create an issue
        Issue issue = createIssue("Test issue");
        issue.status = "open";
        issue.persist();

        // Update status to closed
        Issue result = issueService.updateStatus(issue.id, "closed");

        assertThat(result.status).isEqualTo("closed");
    }

    @Test
    @Transactional
    public void testUpdateStatusInvalid() {
        // Create an issue
        Issue issue = createIssue("Test issue");
        issue.status = "open";
        issue.persist();

        // Try to update to invalid status
        assertThatThrownBy(() -> {
            issueService.updateStatus(issue.id, "invalid_status");
        })
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid status: invalid_status. Must be 'open' or 'closed'");
    }

    @Test
    @Transactional
    public void testReparentSuccess() {
        Issue child = createIssue("Child issue");
        Issue parent = createIssue("New parent issue");

        Issue result = issueService.reparent(child.id, parent.id);

        assertThat(result.parent).isNotNull();
        assertThat(result.parent.id).isEqualTo(parent.id);
    }

    @Test
    @Transactional
    public void testReparentToClosedParent() {
        Issue child = createIssue("Child issue");
        Issue parent = createIssue("Closed parent issue");
        parent.status = "closed";
        parent.persist();

        Issue result = issueService.reparent(child.id, parent.id);

        assertThat(result.parent.id).isEqualTo(parent.id);
    }

    @Test
    @Transactional
    public void testReparentUnparent() {
        Issue child = createIssue("Child issue");
        Issue parent = createIssue("Parent issue");
        issueService.reparent(child.id, parent.id);

        Issue result = issueService.reparent(child.id, null);

        assertThat(result.parent).isNull();
    }

    @Test
    @Transactional
    public void testReparentSelfRejected() {
        Issue issue = createIssue("Issue A");

        assertThatThrownBy(() -> issueService.reparent(issue.id, issue.id))
                .isInstanceOf(CycleDetectedException.class)
                .hasMessage("Cannot reparent issue " + issue.id + " under issue " + issue.id
                        + ": would create a cycle");
    }

    @Test
    @Transactional
    public void testReparentTwoCycleRejected() {
        // A is child of B; moving B under A would create the cycle A -> B -> A
        Issue issueA = createIssue("Issue A");
        Issue issueB = createIssue("Issue B");
        issueService.reparent(issueA.id, issueB.id);

        assertThatThrownBy(() -> issueService.reparent(issueB.id, issueA.id))
                .isInstanceOf(CycleDetectedException.class)
                .hasMessage("Cannot reparent issue " + issueB.id + " under issue " + issueA.id + ": would create a cycle");
    }

    @Test
    @Transactional
    public void testReparentThreeCycleRejected() {
        // A -> B -> C written directly: growing the chain through the service is
        // now rejected by the depth cap, so cycle prevention is exercised on the
        // shape a legacy chain has. Moving C under A closes A -> B -> C -> A.
        Issue issueA = createIssue("Issue A");
        Issue issueB = createIssue("Issue B");
        Issue issueC = createIssue("Issue C");
        linkParent(issueA, issueB);
        linkParent(issueB, issueC);

        assertThatThrownBy(() -> issueService.reparent(issueC.id, issueA.id))
                .isInstanceOf(CycleDetectedException.class);
    }

    @Test
    @Transactional
    public void testReparentNonexistentParentRejected() {
        Issue issue = createIssue("Issue A");

        assertThatThrownBy(() -> issueService.reparent(issue.id, 999999L))
                .isInstanceOf(IssueService.NotFoundException.class)
                .hasMessage("Parent issue not found: 999999");
    }

    @Test
    @Transactional
    public void testReparentNonexistentIssueRejected() {
        assertThatThrownBy(() -> issueService.reparent(999999L, null))
                .isInstanceOf(IssueService.NotFoundException.class)
                .hasMessage("Issue not found: 999999");
    }

    // ── Hierarchy depth (one-level rule) ────────────────────────────────────

    @Test
    @Transactional
    public void testCreateIssueUnderRootAllowed() {
        Issue root = createIssue("Root issue");

        Issue created = issueService.createIssue("Child issue", null, root.id, null);

        assertThat(created.parent.id).isEqualTo(root.id);
    }

    @Test
    @Transactional
    public void testCreateIssueUnderChildRejected() {
        Issue root = createIssue("Root issue");
        Issue child = createIssue("Child issue");
        issueService.reparent(child.id, root.id);

        assertThatThrownBy(() -> issueService.createIssue("Grandchild issue", null, child.id, null))
                .isInstanceOf(HierarchyDepthExceededException.class)
                .hasMessage("Cannot set parent to issue " + child.id
                        + ": max hierarchy depth is " + IssueService.MAX_HIERARCHY_DEPTH);
    }

    @Test
    @Transactional
    public void testReparentUnderChildRejected() {
        Issue root = createIssue("Root issue");
        Issue child = createIssue("Child issue");
        issueService.reparent(child.id, root.id);
        Issue other = createIssue("Other issue");

        assertThatThrownBy(() -> issueService.reparent(other.id, child.id))
                .isInstanceOf(HierarchyDepthExceededException.class)
                .hasMessage("Cannot set parent to issue " + child.id
                        + ": max hierarchy depth is " + IssueService.MAX_HIERARCHY_DEPTH);
    }

    @Test
    @Transactional
    public void testReparentChildUnderAnotherRootAllowed() {
        // A child issue moves from one parent to another
        Issue firstRoot = createIssue("First parent");
        Issue secondRoot = createIssue("Second parent");
        Issue child = createIssue("Child");
        issueService.reparent(child.id, firstRoot.id);

        Issue result = issueService.reparent(child.id, secondRoot.id);

        assertThat(result.parent.id).isEqualTo(secondRoot.id);
    }

    @Test
    @Transactional
    public void testReparentPopulatedRootUnderAnotherRootRejected() {
        // The new parent is a root, but the move would drag its children past the
        // cap — the cap applies to the deepest issue the move would produce
        Issue parent = createIssue("Populated root");
        createChildOf(parent);
        Issue otherRoot = createIssue("Other root");

        assertThatThrownBy(() -> issueService.reparent(parent.id, otherRoot.id))
                .isInstanceOf(HierarchyDepthExceededException.class)
                .hasMessage("Cannot set parent to issue " + otherRoot.id
                        + ": max hierarchy depth is " + IssueService.MAX_HIERARCHY_DEPTH);
    }

    @Test
    @Transactional
    public void testLegacyChildCanMoveUnderRoot() {
        // Legacy chains are not trapped: re-parenting the deepest issue
        // onto a root shortens the chain, so it stays allowed
        Issue deepest = createChain("Legacy issue", 3);
        Issue root = createIssue("Fresh root");

        Issue result = issueService.reparent(deepest.id, root.id);

        assertThat(result.parent.id).isEqualTo(root.id);
    }

    @Test
    @Transactional
    public void testLegacyIssueCanKeepItsParent() {
        // Re-setting the parent a legacy issue already has changes no depth,
        // so it stays allowed — the legacy rule means pre-existing chains keep
        // working as they did, not only that they can be read
        Issue deepest = createChain("Legacy issue", 3);
        Long existingParentId = deepest.parent.id;

        Issue result = issueService.reparent(deepest.id, existingParentId);

        assertThat(result.parent.id).isEqualTo(existingParentId);
    }

    @Test
    @Transactional
    public void testDeepenLegacyChainRejected() {
        // Legacy status is not a loophole: the chain can stay as it is or shallow
        // out, but growing it deeper than the cap is still rejected
        Issue deepest = createChain("Legacy issue", 3);

        assertThatThrownBy(() -> issueService.createIssue("New deepest", null, deepest.id, null))
                .isInstanceOf(HierarchyDepthExceededException.class);
    }

    /**
     * Persist a chain of the given length by writing the parent links directly,
     * bypassing the service rule. That is the shape of a legacy chain —
     * one deeper than {@link IssueService#MAX_HIERARCHY_DEPTH} now allows.
     *
     * @return the deepest issue of the chain
     */
    private Issue createChain(String titlePrefix, int length) {
        Issue previous = null;
        for (int i = 0; i < length; i++) {
            Issue issue = createIssue(titlePrefix + " " + i);
            if (previous != null) {
                linkParent(issue, previous);
            }
            previous = issue;
        }
        return previous;
    }

    /** Write a parent link directly, bypassing the service's depth rule. */
    private void linkParent(Issue child, Issue parent) {
        child.parent = parent;
        child.persist();
    }

    private Issue createChildOf(Issue parent) {
        Issue child = createIssue("Child of " + parent.title);
        issueService.reparent(child.id, parent.id);
        return child;
    }

    @Test
    @Transactional
    public void testAddCommentSuccess() {
        // Create an issue
        Issue issue = createIssue("Test issue");

        // Add a valid comment
        var comment = issueService.addComment(issue.id, "alice", "This is a valid comment body.");

        assertThat(comment.author).isEqualTo("alice");
        assertThat(comment.body).isEqualTo("This is a valid comment body.");
        assertThat(comment.issue.id).isEqualTo(issue.id);
    }

    @Test
    @Transactional
    public void testAddCommentBodyRequired() {
        Issue issue = createIssue("Test issue");

        // Try with null body
        assertThatThrownBy(() -> {
            issueService.addComment(issue.id, "alice", null);
        })
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Comment body is required");
    }

    @Test
    @Transactional
    public void testAddCommentBodyBlank() {
        Issue issue = createIssue("Test issue");

        // Try with blank body
        assertThatThrownBy(() -> {
            issueService.addComment(issue.id, "alice", "   ");
        })
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Comment body is required");
    }

    @Test
    @Transactional
    public void testAddCommentBodyEmpty() {
        Issue issue = createIssue("Test issue");

        // Try with empty body
        assertThatThrownBy(() -> {
            issueService.addComment(issue.id, "alice", "");
        })
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Comment body is required");
    }

    @Test
    @Transactional
    public void testAddCommentBodyTrimmed() {
        Issue issue = createIssue("Test issue");

        // Add comment with whitespace around body
        var comment = issueService.addComment(issue.id, "alice", "  Trimmed body  ");

        assertThat(comment.body).isEqualTo("Trimmed body");
    }

    // ── Detail view: the children the detail payload embeds ──────────────

    @Test
    @Transactional
    public void testGetIssueWithChildrenEmbedsAllStatusesOldestCreatedFirst() {
        Issue parent = createIssue("Parent issue");
        Instant now = Instant.now();
        Issue oldest = childOf(parent, now.minus(3, ChronoUnit.MINUTES), "open");
        Issue middle = childOf(parent, now.minus(2, ChronoUnit.MINUTES), "open");
        Issue closed = childOf(parent, now.minus(1, ChronoUnit.MINUTES), "closed");

        IssueService.IssueWithChildren detail = issueService.getIssueWithChildren(parent.id);

        assertThat(detail.issue().id).isEqualTo(parent.id);
        assertThat(detail.children())
                .extracting(child -> child.id)
                .containsExactly(oldest.id, middle.id, closed.id);
        assertThat(detail.children())
                .extracting(child -> child.status)
                .contains("closed");
    }

    @Test
    @Transactional
    public void testGetIssueWithChildrenTieBreaksOnIdAscending() {
        // Two children created in the same millisecond tie on createdAt; the id
        // tie-breaker traveling with the direction is what keeps their order
        // deterministic — ascending here, the child order's direction.
        Issue parent = createIssue("Parent issue");
        Instant shared = Instant.now();
        Issue first = childOf(parent, shared, "open");
        Issue second = childOf(parent, shared, "open");

        assertThat(issueService.getIssueWithChildren(parent.id).children())
                .extracting(child -> child.id)
                .containsExactlyInAnyOrder(first.id, second.id)
                .isSorted();
    }

    @Test
    @Transactional
    public void testGetIssueWithChildrenWithoutChildren() {
        Issue parent = createIssue("Parent issue");
        createIssue("Unrelated issue");

        IssueService.IssueWithChildren detail = issueService.getIssueWithChildren(parent.id);

        assertThat(detail.children()).isEmpty();
    }

    @Test
    @Transactional
    public void testGetIssueWithChildrenIsUncapped() {
        // The list endpoint paginates; the detail payload embeds every child
        Issue parent = createIssue("Parent issue");
        Instant now = Instant.now();
        for (int i = 0; i < 60; i++) {
            childOf(parent, now.minus(i, ChronoUnit.MINUTES), "open");
        }

        assertThat(issueService.getIssueWithChildren(parent.id).children()).hasSize(60);
    }

    /**
     * A child of the parent, written straight to the store with the timestamp and
     * status it is given — createdAt and updatedAt together, since the children
     * query orders on createdAt and nothing here updates the child afterwards.
     */
    private Issue childOf(Issue parent, Instant timestamp, String status) {
        Issue child = new Issue();
        child.title = "Child of " + parent.title;
        child.parent = parent;
        child.status = status;
        child.createdAt = timestamp;
        child.updatedAt = timestamp;
        child.persist();
        return child;
    }
}
