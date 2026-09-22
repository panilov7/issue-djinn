package com.example.issuedjinn.domain.repository;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link IssueFilterSpec}: the predicate each filter factory
 * produces, the parameters it binds, and how specs compose. Pure functions —
 * no Quarkus context needed.
 *
 * <p>The fragment text is the contract here: the repository renders whatever
 * these specs produce, so a changed fragment is a changed query. These tests
 * therefore describe the rendering, not just the behaviour — a change of query
 * language (say, a move to JPA Criteria) rewrites them along with the spec.
 */
public class IssueFilterSpecTest {

    /** A JPQL named parameter: {@code :status}, {@code :label_0}, {@code :searchId}. */
    private static final Pattern PARAMETER_NAME = Pattern.compile(":([a-zA-Z_][a-zA-Z0-9_]*)");

    @Test
    void aNamedStatusFiltersByExactStatus() {
        IssueFilterSpec spec = IssueFilterSpec.status("open");

        assertThat(spec.whereFragment()).isEqualTo("i.status = :status");
        assertThat(spec.parameters()).containsEntry("status", "open");
        assertThat(spec.isEmpty()).isFalse();
    }

    @Test
    void anAbsentStatusContributesNothing() {
        assertThat(IssueFilterSpec.status(null).isEmpty()).isTrue();
        assertThat(IssueFilterSpec.status(null).parameters()).isEmpty();
    }

    @Test
    void composedSpecsANDTogetherWithMergedParameters() {
        IssueFilterSpec spec = IssueFilterSpec.status("open").and(IssueFilterSpec.parent(7L));

        assertThat(spec.whereFragment()).isEqualTo("i.status = :status AND i.parent.id = :parentId");
        assertThat(spec.parameters()).containsEntry("status", "open").containsEntry("parentId", 7L);
    }

    @Test
    void composingWithAnEmptySpecChangesNothing() {
        IssueFilterSpec spec = IssueFilterSpec.status("open");

        assertThat(spec.and(IssueFilterSpec.none())).isSameAs(spec);
        assertThat(IssueFilterSpec.none().and(spec)).isSameAs(spec);
    }

    @Test
    void compositionDoesNotMutateTheOperands() {
        IssueFilterSpec status = IssueFilterSpec.status("open");

        status.and(IssueFilterSpec.parent(7L));

        assertThat(status.whereFragment()).isEqualTo("i.status = :status");
    }

    @Test
    void eachLabelGetsItsOwnMemberOfPredicate() {
        IssueFilterSpec spec = IssueFilterSpec.labels(List.of("ready-for-agent", "task"));

        assertThat(spec.whereFragment())
                .isEqualTo(":label_0 MEMBER OF i.labels AND :label_1 MEMBER OF i.labels");
        assertThat(spec.parameters())
                .containsEntry("label_0", "ready-for-agent")
                .containsEntry("label_1", "task");
    }

    /** A repeated label is still one predicate per occurrence, so both apply. */
    @Test
    void duplicateLabelsGetDistinctParameters() {
        IssueFilterSpec spec = IssueFilterSpec.labels(List.of("ready-for-agent", "ready-for-agent"));

        assertThat(spec.whereFragment())
                .isEqualTo(":label_0 MEMBER OF i.labels AND :label_1 MEMBER OF i.labels");
        assertThat(spec.parameters())
                .containsEntry("label_0", "ready-for-agent")
                .containsEntry("label_1", "ready-for-agent");
    }

    @Test
    void noLabelsContributesNothing() {
        assertThat(IssueFilterSpec.labels(List.of()).isEmpty()).isTrue();
        assertThat(IssueFilterSpec.labels(List.of()).parameters()).isEmpty();
    }

    /** {@code IssueQuery.labels} is a public nullable field, so a null list reaches the fold. */
    @Test
    void aNullLabelListContributesNothing() {
        assertThat(IssueFilterSpec.labels(null).isEmpty()).isTrue();
    }

    @Test
    void aNamedAssigneeFiltersByExactAssignee() {
        IssueFilterSpec spec = IssueFilterSpec.assignee("agent-64", null);

        assertThat(spec.whereFragment()).isEqualTo("i.assignee = :assignee");
        assertThat(spec.parameters()).containsEntry("assignee", "agent-64");
    }

    @Test
    void thePresenceFlagFiltersByAssigneePresence() {
        assertThat(IssueFilterSpec.assignee(null, true).whereFragment()).isEqualTo("i.assignee IS NOT NULL");
        assertThat(IssueFilterSpec.assignee(null, false).whereFragment()).isEqualTo("i.assignee IS NULL");
        assertThat(IssueFilterSpec.assignee(null, true).parameters()).isEmpty();
    }

    /** An exact assignee takes precedence over the presence flag. */
    @Test
    void aNamedAssigneeWinsOverThePresenceFlag() {
        IssueFilterSpec spec = IssueFilterSpec.assignee("agent-64", false);

        assertThat(spec.whereFragment()).isEqualTo("i.assignee = :assignee");
        assertThat(spec.parameters()).containsOnlyKeys("assignee");
    }

    @Test
    void anAbsentAssigneeAndFlagContributeNothing() {
        assertThat(IssueFilterSpec.assignee(null, null).isEmpty()).isTrue();
    }

    /** An empty assignee name is not a name, so the flag decides instead. */
    @Test
    void anEmptyAssigneeFallsThroughToThePresenceFlag() {
        assertThat(IssueFilterSpec.assignee("", true).whereFragment()).isEqualTo("i.assignee IS NOT NULL");
    }

    /** "Has open dependencies" = at least one issue this one depends on is open. */
    @Test
    void theOpenDependencyFlagFiltersThroughAnExistsSubquery() {
        String exists = "EXISTS (SELECT 1 FROM Issue d JOIN d.dependents dep WHERE dep = i AND d.status = 'open')";

        assertThat(IssueFilterSpec.hasOpenDependency(true).whereFragment()).isEqualTo(exists);
        assertThat(IssueFilterSpec.hasOpenDependency(false).whereFragment()).isEqualTo("NOT " + exists);
        assertThat(IssueFilterSpec.hasOpenDependency(true).parameters()).isEmpty();
    }

    @Test
    void anAbsentOpenDependencyFlagContributesNothing() {
        assertThat(IssueFilterSpec.hasOpenDependency(null).isEmpty()).isTrue();
    }

    @Test
    void aSearchTermMatchesTitleAndDescriptionCaseInsensitively() {
        IssueFilterSpec spec = IssueFilterSpec.search(Optional.of("Quarkus"), OptionalLong.empty());

        assertThat(spec.whereFragment())
                .isEqualTo("(LOWER(i.title) LIKE :search OR LOWER(i.description) LIKE :search)");
        assertThat(spec.parameters()).containsEntry("search", "%quarkus%");
    }

    /** A term that names an issue id also matches that issue by exact id. */
    @Test
    void aTermThatNamesAnIssueAddsAnIdAlternative() {
        IssueFilterSpec spec = IssueFilterSpec.search(Optional.of("36"), OptionalLong.of(36));

        assertThat(spec.whereFragment()).isEqualTo(
                "(LOWER(i.title) LIKE :search OR LOWER(i.description) LIKE :search OR i.id = :searchId)");
        assertThat(spec.parameters())
                .containsEntry("search", "%36%")
                .containsEntry("searchId", 36L);
    }

    @Test
    void anAbsentSearchTermContributesNothing() {
        assertThat(IssueFilterSpec.search(Optional.empty(), OptionalLong.empty()).isEmpty()).isTrue();
        // The id half belongs to a term: with no term there is no id match either.
        assertThat(IssueFilterSpec.search(Optional.empty(), OptionalLong.of(36)).isEmpty()).isTrue();
    }

    // ── Composition invariants ────────────────────────────────────────────

    /**
     * The property the fragment+bindings pairing exists for: every parameter a
     * composed spec names is bound, and no binding is orphaned. (A parameter
     * may be referenced more than once — the search term matches two columns
     * from one binding — so this compares distinct names.) If a factory and its
     * binding ever drift apart, this is where it shows.
     */
    @Test
    void everyNamedParameterIsBoundAndEveryBindingIsNamed() {
        IssueFilterSpec spec = IssueFilterSpec.none()
                .and(IssueFilterSpec.status("open"))
                .and(IssueFilterSpec.parent(7L))
                .and(IssueFilterSpec.labels(List.of("ready-for-agent", "task")))
                .and(IssueFilterSpec.assignee("agent-64", false))
                .and(IssueFilterSpec.hasOpenDependency(false))
                .and(IssueFilterSpec.search(Optional.of("36"), OptionalLong.of(36)));

        assertThat(parameterNamesIn(spec.whereFragment()))
                .containsExactlyInAnyOrderElementsOf(spec.parameters().keySet());
    }

    /**
     * A query whose every filter is absent composes to nothing, which is what
     * lets the repository fall back to an unfiltered find.
     */
    @Test
    void aCompositionOfOnlyAbsentFiltersIsEmpty() {
        IssueFilterSpec spec = IssueFilterSpec.none()
                .and(IssueFilterSpec.status(null))
                .and(IssueFilterSpec.parent(null))
                .and(IssueFilterSpec.labels(List.of()))
                .and(IssueFilterSpec.assignee(null, null))
                .and(IssueFilterSpec.hasOpenDependency(null))
                .and(IssueFilterSpec.search(Optional.empty(), OptionalLong.empty()));

        assertThat(spec.isEmpty()).isTrue();
        assertThat(spec.whereFragment()).isEmpty();
        assertThat(spec.parameters()).isEmpty();
    }

    /** Parameters are named by their factories, so a collision is a bug — not a silent overwrite. */
    @Test
    void twoFiltersBindingTheSameNameFailLoudly() {
        assertThatThrownBy(() -> IssueFilterSpec.status("open").and(IssueFilterSpec.status("closed")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("status");
    }

    // ── Re-anchoring onto another alias ───────────────────────────────────

    /**
     * The children of a root satisfy the same filters the root does, so one
     * filter definition has to serve two aliases: {@code anchoredAt} rewrites
     * the predicates onto the child alias while keeping the bindings. Every
     * factory's shape gets a turn, since each writes {@code i} differently.
     */
    @Test
    void reanchoringRewritesEveryPredicateOntoTheNewAlias() {
        assertThat(IssueFilterSpec.status("open").anchoredAt("c").whereFragment())
                .isEqualTo("c.status = :status");
        assertThat(IssueFilterSpec.parent(7L).anchoredAt("c").whereFragment())
                .isEqualTo("c.parent.id = :parentId");
        assertThat(IssueFilterSpec.labels(List.of("ready-for-agent")).anchoredAt("c").whereFragment())
                .isEqualTo(":label_0 MEMBER OF c.labels");
        assertThat(IssueFilterSpec.assignee("agent-64", null).anchoredAt("c").whereFragment())
                .isEqualTo("c.assignee = :assignee");
        assertThat(IssueFilterSpec.assignee(null, false).anchoredAt("c").whereFragment())
                .isEqualTo("c.assignee IS NULL");
        assertThat(IssueFilterSpec.hasOpenDependency(false).anchoredAt("c").whereFragment())
                .isEqualTo("NOT EXISTS (SELECT 1 FROM Issue d JOIN d.dependents dep"
                        + " WHERE dep = c AND d.status = 'open')");
        assertThat(IssueFilterSpec.search(Optional.of("36"), OptionalLong.of(36))
                .anchoredAt("c").whereFragment())
                .isEqualTo("(LOWER(c.title) LIKE :search OR LOWER(c.description) LIKE :search"
                        + " OR c.id = :searchId)");
    }

    /** The bindings are the filter's own values, whatever alias the predicates name. */
    @Test
    void reanchoringKeepsTheParameters() {
        IssueFilterSpec anchored = IssueFilterSpec.status("open")
                .and(IssueFilterSpec.search(Optional.of("36"), OptionalLong.of(36)))
                .anchoredAt("c");

        assertThat(anchored.parameters())
                .containsEntry("status", "open")
                .containsEntry("search", "%36%")
                .containsEntry("searchId", 36L);
    }

    /** The root alias is already where the predicates live. */
    @Test
    void reanchoringOntoTheRootAliasChangesNothing() {
        IssueFilterSpec spec = IssueFilterSpec.status("open");

        assertThat(spec.anchoredAt("i")).isSameAs(spec);
    }

    /** An absent filter has no predicates to move. */
    @Test
    void reanchoringAnEmptySpecChangesNothing() {
        assertThat(IssueFilterSpec.none().anchoredAt("c")).isSameAs(IssueFilterSpec.none());
    }

    /** The distinct named parameters a fragment refers to, in order of first appearance. */
    private static List<String> parameterNamesIn(String fragment) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = PARAMETER_NAME.matcher(fragment);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return List.copyOf(names);
    }
}
