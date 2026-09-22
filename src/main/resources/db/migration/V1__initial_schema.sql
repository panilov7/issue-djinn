-- =====================================================================
-- V1: Initial schema for issue-djinn
-- Creates the core tables: issues, issue_dependencies, comments, issue_labels
-- No seed data - database starts empty (ADR-0002)
-- =====================================================================

-- =====================================================================
-- Table: issues
-- Represents an issue in the system. Distinguished into spec/map/ticket by labels and parent reference.
-- =====================================================================
CREATE TABLE issues (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  -- Reference to parent issue for wayfinder maps; NULL for root issues
  parent_id   INTEGER REFERENCES issues(id) ON DELETE CASCADE,
  -- Issue title; required for all issues
  title       TEXT NOT NULL,
  -- Issue description; optional on create but defaults to empty string
  description TEXT NOT NULL DEFAULT '',
  -- Issue status; must be 'open' or 'closed'
  status      TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open','closed')),
  -- Claimer's name; free string, nullable
  assignee    TEXT,
  -- Issue creation timestamp in epoch milliseconds (UTC)
  created_at  INTEGER NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000 AS INTEGER)),
  -- Issue last update timestamp in epoch milliseconds (UTC)
  updated_at  INTEGER NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000 AS INTEGER))
);

-- Index for efficient parent lookup
CREATE INDEX idx_issues_parent ON issues(parent_id);

-- Index for filtering by status and assignee
CREATE INDEX idx_issues_status_assignee ON issues(status, assignee);

-- Index for sorting by most recently updated
CREATE INDEX idx_issues_updated_at ON issues(updated_at DESC);

-- =====================================================================
-- Table: issue_dependencies
-- Join table for dependency edges between issues. Prevents self-reference via CHECK constraint.
-- Cycle prevention is enforced in the service layer using a recursive CTE.
-- Column order: dependent_id first, dependency_id second (A depends on B means A → B in the edge).
-- =====================================================================
CREATE TABLE issue_dependencies (
  dependent_id   INTEGER NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
  dependency_id  INTEGER NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
  PRIMARY KEY (dependent_id, dependency_id),
  CHECK (dependent_id <> dependency_id)
);

-- Index for efficient lookup of issues that depend on a given issue
CREATE INDEX idx_issue_dependencies_dependent ON issue_dependencies(dependent_id);

-- =====================================================================
-- Table: comments
-- Markdown comments associated with an issue.
-- =====================================================================
CREATE TABLE comments (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  -- Reference to the issue this comment belongs to
  issue_id    INTEGER NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
  -- Comment author; free string, e.g. "pi", "matt", "triage"
  author      TEXT NOT NULL,
  -- Comment body in markdown format
  body        TEXT NOT NULL,
  -- Comment creation timestamp in epoch milliseconds (UTC)
  created_at  INTEGER NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000 AS INTEGER))
);

-- Index for efficient lookup of comments for a given issue, ordered by creation time
CREATE INDEX idx_comments_issue ON comments(issue_id, created_at);

-- =====================================================================
-- Table: issue_labels
-- Opaque string labels for issues. No enum or whitelist (ADR-0001).
-- =====================================================================
CREATE TABLE issue_labels (
  issue_id    INTEGER NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
  label       TEXT NOT NULL,
  PRIMARY KEY (issue_id, label)
);

-- Index for efficient lookup of issues by label
CREATE INDEX idx_issue_labels_label ON issue_labels(label);
