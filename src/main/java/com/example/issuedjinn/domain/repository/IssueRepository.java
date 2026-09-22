package com.example.issuedjinn.domain.repository;

import com.example.issuedjinn.domain.entity.Issue;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Repository for Issue entities.
 *
 * <p>Listing is built as a set of AND-composed filter specs over the Issue root
 * (see {@link IssueFilterSpec}), so every filter is written once regardless of
 * which other filters are active. An absent filter contributes no predicate,
 * and every listing is ordered by the one ordering rule — {@code ORDER BY
 * <field> <direction>, id <direction>} (see {@link IssueOrder}): parent-level
 * rows newest-created-first, children oldest-created-first. Per-filter
 * semantics — label matching, assignee precedence, what "has open dependencies"
 * means — live with their factories on {@link IssueFilterSpec}.
 */
@ApplicationScoped
public class IssueRepository implements PanacheRepositoryBase<Issue, Long> {

    private static final String SELECT_ISSUES = "SELECT DISTINCT i FROM Issue i";

    /** A root: an issue with no parent (CONTEXT.md → Root). */
    private static final String ROOTS_ONLY = "i.parent IS NULL";

    /**
     * The alias the children of a root are read under — in the child counts,
     * in the children a list row groups, and in the EXISTS that spots a root
     * with a matching child.
     */
    private static final String CHILD_ALIAS = "c";

    /**
     * One grouped count per parent and status; the service folds the statuses
     * of a parent into one {@link ChildCounts}.
     */
    private static final String CHILD_COUNTS = """
            SELECT c.parent.id, c.status, COUNT(c)
            FROM Issue c
            WHERE c.parent.id IN :parentIds
            GROUP BY c.parent.id, c.status
            """;

    /**
     * Find issues with optional filters — each matching issue as its own row,
     * however deep it sits. The grouped issues list is built on
     * {@link #findRootPage} instead; this one serves the queries that name a
     * parent and so ask for that parent's children as their own rows.
     */
    public List<Issue> findWithFilters(IssueQuery query) {
        IssueFilterSpec filter = filtersOf(query);
        return findIssues(
                filter.isEmpty() ? List.of() : List.of(filter.whereFragment()),
                filter.parameters(),
                query);
    }

    /**
     * The page of roots the grouped issues list shows.
     *
     * <p>A root is on the page when it matches the filters itself, or when at
     * least one of its first-level children does — the second half is what keeps
     * a filtered-in child from being hidden by a filtered-out parent. Which of the
     * two ways got a root onto the page is {@link #idsMatchingFilters}' answer,
     * not this query's: here it only decides who is listed.
     *
     * <p>Pagination counts roots only. The limit and offset are roots, and
     * children ride along with their root rather than taking a page slot — the
     * children themselves are {@link #findMatchingChildren}'s business.
     */
    public List<Issue> findRootPage(IssueQuery query) {
        IssueFilterSpec filter = filtersOf(query);
        List<String> conditions = new ArrayList<>();
        conditions.add(ROOTS_ONLY);
        if (!filter.isEmpty()) {
            // One disjunction wrapping both alternatives, so the AND-composed
            // filter keeps its scope: JPQL binds AND tighter than OR, which
            // groups each side as it stands — hence the two unparenthesized
            // halves rather than one paren per side inside the outer paren.
            conditions.add("(" + filter.whereFragment() + " OR " + matchingFirstChild(filter) + ")");
        }
        return findIssues(conditions, filter.parameters(), query);
    }

    /**
     * The ids of the given issues that match the filters themselves — as opposed
     * to being on a page through a matching child, which is how the list knows
     * which roots to flag as containers rather than rows.
     *
     * <p>With no filter active every given id matches, which needs no query.
     */
    public Set<Long> idsMatchingFilters(Collection<Long> ids, IssueQuery query) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        IssueFilterSpec filter = filtersOf(query);
        if (filter.isEmpty()) {
            return new HashSet<>(ids);
        }

        String jpql = "SELECT i.id FROM Issue i WHERE i.id IN :ids AND " + filter.whereFragment();
        TypedQuery<Long> jpaQuery = getEntityManager().createQuery(jpql, Long.class);
        jpaQuery.setParameter("ids", ids);
        filter.parameters().forEach(jpaQuery::setParameter);
        return new HashSet<>(jpaQuery.getResultList());
    }

    /**
     * The first-level children of the given parents that match the filters, in
     * the query's child order (oldest-created-first by default, see
     * {@link IssueQuery#childOrder()}) — the children a list row groups under
     * its root.
     *
     * <p>The filters are the root filters re-anchored onto the child alias (see
     * {@link IssueFilterSpec#anchoredAt}), so a child qualifies exactly as a root
     * would. Deeper descendants are not children of the given parents and so are
     * never selected, which is what keeps the list to one grouped level.
     *
     * <p>One query for the whole page, and uncapped: the per-root cap is the
     * service's grouping decision, and the children a cap hides still have to be
     * counted. A parent whose children all miss the filters contributes nothing.
     */
    public List<Issue> findMatchingChildren(Collection<Long> parentIds, IssueQuery query) {
        if (parentIds.isEmpty()) {
            return List.of();
        }

        IssueFilterSpec filter = filtersOf(query);
        String conditions = "c.parent.id IN :parentIds" + andChildFilter(filter);
        String jpql = "SELECT DISTINCT c FROM Issue c WHERE " + conditions
                + query.childOrder().fragmentFor(CHILD_ALIAS);

        TypedQuery<Issue> jpaQuery = getEntityManager().createQuery(jpql, Issue.class);
        jpaQuery.setParameter("parentIds", parentIds);
        filter.parameters().forEach(jpaQuery::setParameter);
        return jpaQuery.getResultList();
    }

    /**
     * The children of the given parent, in the given child order (oldest-created-first
     * by default) — the order the detail payload embeds them in. Uncapped: the
     * list endpoint paginates, a parent's children do not.
     */
    public List<Issue> findChildrenOf(Long parentId, IssueOrder order) {
        IssueFilterSpec parent = IssueFilterSpec.parent(parentId).anchoredAt(CHILD_ALIAS);
        String jpql = "SELECT c FROM Issue c WHERE " + parent.whereFragment()
                + order.fragmentFor(CHILD_ALIAS);

        TypedQuery<Issue> jpaQuery = getEntityManager().createQuery(jpql, Issue.class);
        parent.parameters().forEach(jpaQuery::setParameter);
        return jpaQuery.getResultList();
    }

    /**
     * The first-level child counts of the given issues, keyed by issue id.
     *
     * <p>One grouped query for the whole page — a parent's counts are not a
     * per-parent query, so a page of roots costs one extra round trip however
     * many roots it shows. Only the children of the given ids are counted, and
     * nothing else about them is read: the counts deliberately see none of the
     * filters that selected the parents, because they answer "how much work
     * sits under this root?" rather than "how many rows match right now?".
     * A parent with no children is absent from the map.
     */
    public Map<Long, ChildCounts> countChildrenByParent(Collection<Long> parentIds) {
        if (parentIds.isEmpty()) {
            return Map.of();
        }

        List<Object[]> rows = getEntityManager().createQuery(CHILD_COUNTS, Object[].class)
                .setParameter("parentIds", parentIds)
                .getResultList();

        Map<Long, ChildCounts> counts = new HashMap<>();
        for (Object[] row : rows) {
            Long parentId = (Long) row[0];
            counts.put(parentId, counts.getOrDefault(parentId, ChildCounts.NONE)
                    .adding((String) row[1], (Long) row[2]));
        }
        return Map.copyOf(counts);
    }

    /**
     * EXISTS a first-level child of the root that matches the filters — any
     * child at all when none is active, since there is then nothing for a child
     * to match. The filters apply to the child exactly as they apply to the
     * root, which is one {@link IssueFilterSpec} anchored onto the child alias.
     */
    private static String matchingFirstChild(IssueFilterSpec filter) {
        return "EXISTS (SELECT 1 FROM Issue " + CHILD_ALIAS
                + " WHERE " + CHILD_ALIAS + ".parent = i" + andChildFilter(filter) + ")";
    }

    /**
     * The child half of the filter, as an AND clause onto the child alias —
     * empty when no filter is active, so a child qualifies exactly as a root
     * would (see {@link IssueFilterSpec#anchoredAt}).
     */
    private static String andChildFilter(IssueFilterSpec filter) {
        return filter.isEmpty() ? "" : " AND " + filter.anchoredAt(CHILD_ALIAS).whereFragment();
    }

    /**
     * The issues the given conditions select, in the query's effective order —
     * the default, or the order the query pinned (see
     * {@link IssueQuery#effectiveOrder()}) — and paginated. An empty condition
     * list is an unfiltered find: no WHERE clause.
     */
    private List<Issue> findIssues(List<String> conditions, Map<String, Object> parameters, IssueQuery query) {
        String order = query.effectiveOrder().fragmentFor("i");
        String jpql = conditions.isEmpty()
                ? SELECT_ISSUES + order
                : SELECT_ISSUES + " WHERE " + String.join(" AND ", conditions) + order;

        TypedQuery<Issue> jpaQuery = getEntityManager().createQuery(jpql, Issue.class);
        parameters.forEach(jpaQuery::setParameter);

        // Sort and pagination are orthogonal to the filters.
        return jpaQuery
                .setFirstResult(query.offset)
                .setMaxResults(query.limit)
                .getResultList();
    }

    /**
     * The filters this query asks for, folded into one spec.
     *
     * <p>Each factory answers for itself whether its filter is present, so the
     * fold is a flat list of the filters rather than a per-filter if-else: an
     * absent filter contributes nothing.
     */
    private static IssueFilterSpec filtersOf(IssueQuery query) {
        return IssueFilterSpec.none()
                .and(IssueFilterSpec.status(query.status))
                .and(IssueFilterSpec.parent(query.parentId))
                .and(IssueFilterSpec.labels(query.normalizedLabels()))
                .and(IssueFilterSpec.assignee(query.assignee, query.hasAssignee))
                .and(IssueFilterSpec.hasOpenDependency(query.hasOpenDependency))
                .and(IssueFilterSpec.search(query.normalizedSearch(), query.searchId()));
    }
}
