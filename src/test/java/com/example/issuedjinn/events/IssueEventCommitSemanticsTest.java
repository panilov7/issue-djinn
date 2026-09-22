package com.example.issuedjinn.events;

import com.example.issuedjinn.domain.entity.Issue;
import com.example.issuedjinn.domain.repository.CommentRepository;
import com.example.issuedjinn.domain.repository.IssueRepository;
import com.example.issuedjinn.domain.service.IssueService;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.subscription.Cancellable;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one test below the HTTP seam: a realistic
 * rollback cannot be induced over HTTP, so this drives {@code IssueService}
 * directly inside a {@link UserTransaction} and asserts that a rolled-back
 * create emits nothing — while a committed create reaches the same subscription.
 */
@QuarkusTest
class IssueEventCommitSemanticsTest {

    @Inject
    IssueChangeBroadcaster broadcaster;

    @Inject
    IssueService issueService;

    @Inject
    UserTransaction userTransaction;

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

    @Test
    void aRolledBackCreateEmitsNoEvent() throws Exception {
        List<IssueEventMessage> received = new CopyOnWriteArrayList<>();
        Cancellable subscription = broadcaster.subscribe().subscribe().with(received::add);
        try {
            userTransaction.begin();
            issueService.createIssue("rolled back create", null, null, null);
            userTransaction.setRollbackOnly();
            userTransaction.rollback();

            assertThat(received).as("a rolled-back mutation must emit nothing").isEmpty();

            // Positive control: a committed create reaches the same subscription,
            // so the empty assertion above is not passing vacuously.
            issueService.createIssue("committed create", null, null, null);
            awaitEvent(received);
            assertThat(received).hasSize(1);
        } finally {
            subscription.cancel();
        }
    }

    @Test
    void aRolledBackUpdateEmitsNoEvent() throws Exception {
        Issue issue = issueService.createIssue("rolled back update target", null, null, null);
        List<IssueEventMessage> received = new CopyOnWriteArrayList<>();
        Cancellable subscription = broadcaster.subscribe().subscribe().with(received::add);
        try {
            userTransaction.begin();
            issueService.updateIssue(issue.id, "a title that never commits", null, null, null,
                    IssueService.ParentUpdate.unchanged());
            userTransaction.setRollbackOnly();
            userTransaction.rollback();

            assertThat(received).as("a rolled-back mutation must emit nothing").isEmpty();

            // Positive control: a committed claim on the same issue reaches the
            // same subscription, so the empty assertion above is not passing vacuously.
            issueService.claimIssue(issue.id, "control-agent");
            awaitEvent(received);
            assertThat(received).hasSize(1);
            assertThat(received.get(0).type()).isEqualTo(IssueChangeEvent.Type.ISSUE_UPDATED.wireName());
            assertThat(received.get(0).issueIds()).containsExactly(issue.id);
        } finally {
            subscription.cancel();
        }
    }

    /**
     * A bare status change has no HTTP surface of its own (close/reopen are the
     * REST/MCP status transitions), so its frame is asserted at the service level
     * on the same subscription the rollback tests use.
     */
    @Test
    void aCommittedStatusChangeEmitsIssueUpdatedWithItsId() throws Exception {
        Issue issue = issueService.createIssue("status change target", null, null, null);
        List<IssueEventMessage> received = new CopyOnWriteArrayList<>();
        Cancellable subscription = broadcaster.subscribe().subscribe().with(received::add);
        try {
            issueService.updateStatus(issue.id, "closed");
            awaitEvent(received);

            assertThat(received).hasSize(1);
            assertThat(received.get(0).type()).isEqualTo(IssueChangeEvent.Type.ISSUE_UPDATED.wireName());
            assertThat(received.get(0).issueIds()).containsExactly(issue.id);
        } finally {
            subscription.cancel();
        }
    }

    private static void awaitEvent(List<IssueEventMessage> received) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(2));
        while (received.isEmpty() && Instant.now().isBefore(deadline)) {
            Thread.sleep(50);
        }
    }
}