package com.example.issuedjinn.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents an issue in the system. A parent reference gives issues one level of hierarchy; labels are free-form.
 */
@Entity
@Table(name = "issues")
public class Issue extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /**
     * Reference to the parent issue for child issues; NULL for root issues.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    public Issue parent;

    /**
     * Issue title; required for all issues.
     */
    @Column(nullable = false)
    public String title;

    /**
     * Issue description; optional on create but defaults to empty string.
     */
    @Column(nullable = false, length = 10000)
    public String description = "";

    /**
     * Issue status; must be 'open' or 'closed'.
     */
    @Column(nullable = false, length = 20)
    public String status = "open";

    /**
     * Claimer's name; free string, nullable.
     */
    @Column(length = 100)
    public String assignee;

    /**
     * Issue creation timestamp in epoch milliseconds (UTC).
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    /**
     * Issue last update timestamp in epoch milliseconds (UTC).
     */
    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    /**
     * Issues that this issue depends on (prerequisites — must be resolved first).
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "issue_dependencies",
        joinColumns = @JoinColumn(name = "dependent_id"),
        inverseJoinColumns = @JoinColumn(name = "dependency_id")
    )
    public Set<Issue> dependencies = new HashSet<>();

    /**
     * Issues that depend on this issue (waiters — can't start until this issue is resolved).
     */
    @ManyToMany(fetch = FetchType.LAZY, mappedBy = "dependencies")
    public Set<Issue> dependents = new HashSet<>();

    /**
     * Opaque string labels for this issue. No enum or whitelist (ADR-0001).
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @Column(name = "label", nullable = false)
    @jakarta.persistence.CollectionTable(name = "issue_labels", joinColumns = @JoinColumn(name = "issue_id"))
    public Set<String> labels = new HashSet<>();

    /**
     * Comments associated with this issue.
     */
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "issue", cascade = CascadeType.ALL)
    public Set<Comment> comments = new HashSet<>();

    public Issue() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * JPA callback to update the updatedAt timestamp on any Issue UPDATE.
     */
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Adds a label to this issue.
     */
    public void addLabel(String label) {
        labels.add(label);
    }

    /**
     * Removes a label from this issue.
     */
    public void removeLabel(String label) {
        labels.remove(label);
    }

    /**
     * Adds a dependency relationship where this issue depends on the given issue.
     * Returns whether the edge is new — a re-add of an edge that already exists
     * changes nothing. Note: Cycle prevention must be done in the service layer
     * before calling this.
     */
    public boolean addDependency(Issue prerequisite) {
        boolean added = dependencies.add(prerequisite);
        if (!prerequisite.dependents.contains(this)) {
            prerequisite.dependents.add(this);
        }
        return added;
    }

    /**
     * Removes a dependency relationship where this issue depends on the given issue.
     * Returns whether the edge existed — a remove of an edge that is not there
     * changes nothing.
     */
    public boolean removeDependency(Issue prerequisite) {
        boolean removed = dependencies.remove(prerequisite);
        prerequisite.dependents.remove(this);
        return removed;
    }

    /**
     * Adds a comment to this issue.
     */
    public void addComment(Comment comment) {
        comments.add(comment);
        comment.setIssue(this);
    }
}
