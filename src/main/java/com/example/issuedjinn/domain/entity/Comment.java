package com.example.issuedjinn.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Markdown comment associated with an issue.
 */
@Entity
@Table(name = "comments")
public class Comment extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /**
     * Reference to the issue this comment belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_id", nullable = false)
    public Issue issue;

    /**
     * Comment author; free string, e.g. "alice", "bob", or an agent name.
     */
    @Column(nullable = false, length = 100)
    public String author;

    /**
     * Comment body in markdown format.
     */
    @Column(nullable = false, length = 50000)
    public String body;

    /**
     * Comment creation timestamp in epoch milliseconds (UTC).
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    public Comment() {
        this.createdAt = Instant.now();
    }

    /**
     * Sets the issue this comment belongs to and maintains the bidirectional relationship.
     */
    public void setIssue(Issue issue) {
        this.issue = issue;
    }
}
