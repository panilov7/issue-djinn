-- =====================================================================
-- V2: Index the ordering paths (parent issue #94, ticket #95)
-- Backs the one ordering rule — ORDER BY <field> <direction>, id <direction> —
-- with composite indexes that carry the id tie-break in the index itself, and
-- retires two indexes that no longer pull their weight.
-- Pure performance seam: no behavioral change; tests assert order behaviorally
-- (the test datasource is Hibernate drop-and-create, so these indexes are
-- invisible to it).
-- =====================================================================

-- =====================================================================
-- Add: composite ordering indexes
-- Each names the sort column(s) followed by id, so the id tie-break is
-- satisfied by the index itself with no sort spilling.
-- =====================================================================

-- Parent-scoped child ordering: children of one parent, oldest-created-first
-- (grouped embeds, ?parent=N rows, detail children).
CREATE INDEX idx_issues_parent_created_at_id ON issues(parent_id, created_at, id);

-- Global orderings: newest-created-first parent-level listings,
-- updatedAt sort, and title sort.
CREATE INDEX idx_issues_created_at_id ON issues(created_at, id);
CREATE INDEX idx_issues_updated_at_id ON issues(updated_at, id);
CREATE INDEX idx_issues_title_id ON issues(title, id);

-- Reverse dependency-edge lookups: "what depends on this issue?".
-- (Forward lookups ride the (dependent_id, dependency_id) PK autoindex.)
CREATE INDEX idx_issue_dependencies_dependency_id ON issue_dependencies(dependency_id, dependent_id);

-- =====================================================================
-- Drop: retired indexes
-- idx_issues_updated_at is superseded by idx_issues_updated_at_id (the
-- composite serves the same scans plus the tie-break).
-- idx_issue_dependencies_dependent is a prefix of the PK (dependent_id,
-- dependency_id), whose autoindex the planner already uses.
-- =====================================================================

DROP INDEX IF EXISTS idx_issues_updated_at;
DROP INDEX IF EXISTS idx_issue_dependencies_dependent;

-- Note: text `search` stays un-indexed deliberately — substring matching over
-- title/description cannot use a b-tree, and FTS/trigram work is deferred to a
-- future ticket (CONTEXT.md → `search` query).
