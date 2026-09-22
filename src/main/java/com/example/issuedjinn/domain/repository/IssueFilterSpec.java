package com.example.issuedjinn.domain.repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Pattern;

/**
 * One AND-composable predicate of the issue list query, carrying its own
 * parameter bindings.
 *
 * <p>A spec pairs a JPQL predicate fragment with the named parameters that
 * fragment refers to, so a filter's clause and its bindings are built together
 * and cannot drift apart — the repository renders whatever it is handed instead
 * of re-walking the conditions to bind them.
 *
 * <p>Every factory tolerates an absent filter by returning {@link #none()},
 * which contributes no predicate, so the caller that picks filters stays a flat
 * list of the filters a query asks for.
 *
 * <p>The fragment text is visible only inside this package: callers compose and
 * pass specs, they never read JPQL, which is what keeps a move to JPA Criteria
 * a swap of this class rather than a rewrite of its callers.
 */
public final class IssueFilterSpec {

    /**
     * The alias every fragment is written against: the issue the query roots on.
     * {@link #anchoredAt} is what moves a spec onto another alias, so a factory
     * never has to spell the alias out.
     */
    private static final String ROOT_ALIAS = "i";

    /** The whole-word root alias — {@code i} in {@code i.status}, not in {@code :searchId}. */
    private static final Pattern ROOT_ALIAS_TOKEN = Pattern.compile("\\bi\\b");

    /** The identity of composition: no predicate, no parameters. */
    private static final IssueFilterSpec NONE = new IssueFilterSpec(List.of(), Map.of());

    private final List<String> fragments;
    private final Map<String, Object> parameters;

    private IssueFilterSpec(List<String> fragments, Map<String, Object> parameters) {
        this.fragments = fragments;
        this.parameters = parameters;
    }

    /**
     * The filter that contributes nothing — the starting point of a
     * composition, and what an absent filter produces.
     */
    public static IssueFilterSpec none() {
        return NONE;
    }

    /**
     * The status filter: exact match on the binary {@code open}/{@code closed}
     * status. An absent status filters nothing — see CONTEXT.md: omitting the
     * status lists both.
     */
    public static IssueFilterSpec status(String status) {
        if (status == null) {
            return none();
        }
        return new IssueFilterSpec(List.of("i.status = :status"), Map.of("status", status));
    }

    /**
     * The parent filter: issues whose parent is the given issue. An absent
     * parent filters nothing.
     */
    public static IssueFilterSpec parent(Long parentId) {
        if (parentId == null) {
            return none();
        }
        return new IssueFilterSpec(List.of("i.parent.id = :parentId"), Map.of("parentId", parentId));
    }

    /**
     * The label filters, one predicate per label, AND-composed: an issue must
     * carry every listed label.
     *
     * <p>The list is expected already normalised — blanks dropped, nulls too
     * (see {@link IssueQuery#normalizedLabels()}) — because a blank label would
     * otherwise match nothing and silently empty the result.
     *
     * <p>A label no issue carries is an empty result, not an error — labels are
     * opaque strings with no server-side whitelist (ADR-0001) — and the filter
     * is not status-aware: a label on a closed issue still matches.
     *
     * <p>Labels are an {@code @ElementCollection}, so there is no IssueLabel
     * entity to root the query on — the predicate is a {@code MEMBER OF} over
     * {@code i.labels}. Each label mints its own parameter name
     * ({@code label_0}, {@code label_1}, …) because two labels cannot share one
     * binding, and a repeated label must still apply twice.
     */
    public static IssueFilterSpec labels(List<String> labels) {
        if (labels == null) {
            return none();
        }

        IssueFilterSpec spec = none();
        for (int i = 0; i < labels.size(); i++) {
            spec = spec.and(predicate(
                    ":" + labelParameter(i) + " MEMBER OF i.labels",
                    labelParameter(i), labels.get(i)));
        }
        return spec;
    }

    /**
     * The assignee filter. An exact assignee takes precedence over the
     * presence flag: when an assignee is named, the flag contributes nothing.
     *
     * <ul>
     *   <li>{@code assignee} set: exact match on the assignee field</li>
     *   <li>{@code hasAssignee=true}: assigned ({@code IS NOT NULL})</li>
     *   <li>{@code hasAssignee=false}: unassigned ({@code IS NULL})</li>
     *   <li>both absent: no filter</li>
     * </ul>
     */
    public static IssueFilterSpec assignee(String assignee, Boolean hasAssignee) {
        if (assignee != null && !assignee.isEmpty()) {
            return predicate("i.assignee = :assignee", "assignee", assignee);
        }
        if (hasAssignee == null) {
            return none();
        }
        return predicate(hasAssignee ? "i.assignee IS NOT NULL" : "i.assignee IS NULL");
    }

    /**
     * The has-open-dependency filter. "Has open dependencies" means at least
     * one issue this issue depends on is still open.
     */
    public static IssueFilterSpec hasOpenDependency(Boolean hasOpenDependency) {
        if (hasOpenDependency == null) {
            return none();
        }
        String openDependencyExists = "EXISTS (SELECT 1 FROM Issue d JOIN d.dependents dep"
                + " WHERE dep = i AND d.status = 'open')";
        return predicate(hasOpenDependency ? openDependencyExists : "NOT " + openDependencyExists);
    }

    /**
     * The search filter: a case-insensitive substring over title and
     * description, OR-ed with an exact id match when the term names one.
     *
     * <p>The term arrives already normalised (see
     * {@link IssueQuery#normalizedSearch()}), and the id half is that same
     * term's id (see {@link IssueQuery#searchId()}), so both halves always
     * describe one search. The spec only lowercases the term into its
     * {@code LIKE} pattern.
     */
    public static IssueFilterSpec search(Optional<String> term, OptionalLong matchingId) {
        if (term.isEmpty()) {
            return none();
        }

        List<String> alternatives = new ArrayList<>(List.of(
                "LOWER(i.title) LIKE :search",
                "LOWER(i.description) LIKE :search"));
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("search", "%" + term.get().toLowerCase(Locale.ROOT) + "%");
        if (matchingId.isPresent()) {
            alternatives.add("i.id = :searchId");
            parameters.put("searchId", matchingId.getAsLong());
        }

        return new IssueFilterSpec(List.of("(" + String.join(" OR ", alternatives) + ")"), parameters);
    }

    /**
     * The same filter, rewritten onto another alias of the same Issue type.
     *
     * <p>The grouped issues list applies one set of filters at two depths — a
     * root must match to be listed, and so must each child it groups — and one
     * filter definition serves both, so the two can never disagree about what
     * "matches" means. The parameters are kept as they are: the alias is the
     * only thing that changes, and the values are the filter's own.
     *
     * @param alias the alias to write the predicates against, e.g. {@code "c"}
     *              for a child issue
     */
    public IssueFilterSpec anchoredAt(String alias) {
        if (fragments.isEmpty() || ROOT_ALIAS.equals(alias)) {
            return this;
        }

        List<String> reanchored = fragments.stream()
                .map(fragment -> ROOT_ALIAS_TOKEN.matcher(fragment).replaceAll(alias))
                .toList();
        return new IssueFilterSpec(reanchored, parameters);
    }

    /**
     * True when this spec contributes no predicate at all.
     */
    public boolean isEmpty() {
        return fragments.isEmpty();
    }

    /**
     * Compose two filters: both predicates apply, AND-ed.
     *
     * <p>Every parameter of a composed spec is bound exactly once, so a factory
     * that reuses another's parameter name fails here rather than silently
     * overwriting its binding.
     */
    public IssueFilterSpec and(IssueFilterSpec other) {
        if (other.fragments.isEmpty()) {
            return this;
        }
        if (fragments.isEmpty()) {
            return other;
        }

        Map<String, Object> merged = new LinkedHashMap<>(parameters);
        other.parameters.forEach((name, value) -> {
            if (merged.containsKey(name)) {
                throw new IllegalStateException("Two composed filters bind the same parameter: " + name);
            }
            merged.put(name, value);
        });

        List<String> composed = new ArrayList<>(fragments);
        composed.addAll(other.fragments);
        return new IssueFilterSpec(composed, merged);
    }

    /**
     * The predicates, AND-joined in the order the filters were composed.
     */
    String whereFragment() {
        return String.join(" AND ", fragments);
    }

    /**
     * The binding for every parameter named in {@link #whereFragment()}.
     */
    Map<String, Object> parameters() {
        return parameters;
    }

    /** A single-predicate filter that binds nothing ({@code IS NULL} forms). */
    private static IssueFilterSpec predicate(String fragment) {
        return new IssueFilterSpec(List.of(fragment), Map.of());
    }

    /** A single-predicate filter that binds one named parameter. */
    private static IssueFilterSpec predicate(String fragment, String name, Object value) {
        return new IssueFilterSpec(List.of(fragment), Map.of(name, value));
    }

    private static String labelParameter(int occurrence) {
        return "label_" + occurrence;
    }
}
