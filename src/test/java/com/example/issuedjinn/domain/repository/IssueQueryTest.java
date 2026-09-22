package com.example.issuedjinn.domain.repository;

import com.example.issuedjinn.rest.api.dto.IssueSearchParams;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the query normalisation rules on {@link IssueQuery}: how a
 * search term resolves to an issue id, how a label list is prepared for
 * filtering, and how the REST sort parameters bind and validate. Pure
 * functions — no Quarkus context needed.
 */
public class IssueQueryTest {

    private static OptionalLong idOf(String search) {
        IssueQuery query = new IssueQuery();
        query.search = search;
        return query.searchId();
    }

    @Test
    void bareIntegerNamesAnIssueId() {
        assertThat(idOf("36")).hasValue(36);
    }

    @Test
    void hashPrefixedIntegerNamesAnIssueId() {
        assertThat(idOf("#36")).hasValue(36);
    }

    @Test
    void surroundingWhitespaceIsStrippedBeforeParsing() {
        assertThat(idOf("  36  ")).hasValue(36);
        assertThat(idOf(" #36 ")).hasValue(36);
    }

    @Test
    void mixedTermsArePlainTextSearches() {
        assertThat(idOf("36 fix")).isEmpty();
        assertThat(idOf("#36 fix")).isEmpty();
        assertThat(idOf("issue 36")).isEmpty();
    }

    @Test
    void nonNumericTermsArePlainTextSearches() {
        assertThat(idOf("quarkus")).isEmpty();
        assertThat(idOf("36.5")).isEmpty();
        assertThat(idOf("")).isEmpty();
        assertThat(idOf("#")).isEmpty();
        assertThat(idOf("   ")).isEmpty();
    }

    @Test
    void nullSearchHasNoIdMatch() {
        assertThat(idOf(null)).isEmpty();
    }

    @Test
    void nonPositiveNumbersAreNeverAnIdMatch() {
        assertThat(idOf("0")).isEmpty();
        assertThat(idOf("-3")).isEmpty();
        assertThat(idOf("#0")).isEmpty();
    }

    @Test
    void aNumberTooLargeToBeAnIdIsIgnoredRatherThanThrown() {
        assertThat(idOf("99999999999999999999")).isEmpty();
    }

    @Test
    void hashPrefixAndWhitespaceDoNotChangeTheMatchedText() {
        // '#36' must behave identically to '36' — including the text half of
        // the search — so the '#', not just the id parse, is stripped.
        assertThat(termOf("36")).contains("36");
        assertThat(termOf("#36")).contains("36");
        assertThat(termOf(" 36 ")).contains("36");
        assertThat(termOf(" #36 ")).contains("36");
    }

    @Test
    void mixedTermsKeepTheirTextUnchanged() {
        assertThat(termOf("36 fix")).contains("36 fix");
        assertThat(termOf("#36 fix")).contains("36 fix");
    }

    private static Optional<String> termOf(String search) {
        IssueQuery query = new IssueQuery();
        query.search = search;
        return query.normalizedSearch();
    }

    // ── Label normalisation ───────────────────────────────────────────────

    private static List<String> labelsOf(String... labels) {
        IssueQuery query = new IssueQuery();
        query.labels = List.of(labels);
        return query.normalizedLabels();
    }

    /** A non-blank label list passes through unchanged, order and duplicates included. */
    @Test
    void nonBlankLabelsPassThroughUnchanged() {
        assertThat(labelsOf("ready-for-agent", "task"))
                .containsExactly("ready-for-agent", "task");
        assertThat(labelsOf("ready-for-agent", "ready-for-agent"))
                .containsExactly("ready-for-agent", "ready-for-agent");
    }

    /** Blank values filter nothing, so they are dropped rather than matched. */
    @Test
    void blankLabelsAreDropped() {
        assertThat(labelsOf("")).isEmpty();
        assertThat(labelsOf("   ")).isEmpty();
        assertThat(labelsOf("ready-for-agent", "")).containsExactly("ready-for-agent");
        assertThat(labelsOf("", "ready-for-agent")).containsExactly("ready-for-agent");
    }

    /** Whitespace around a real label is significant — labels are opaque strings. */
    @Test
    void surroundingWhitespaceOnARealLabelIsKept() {
        assertThat(labelsOf(" ready-for-agent ")).containsExactly(" ready-for-agent ");
    }

    /** A null list filters nothing, as an absent filter should. */
    @Test
    void nullLabelListFiltersNothing() {
        IssueQuery query = new IssueQuery();
        query.labels = null;
        assertThat(query.normalizedLabels()).isEmpty();
    }

    /**
     * A null element is dropped rather than blown up on. MCP callers send JSON,
     * where {@code "labels": [null]} deserialises to a list holding a null —
     * {@code List.of} would reject it, so this list is built by hand.
     */
    @Test
    void nullLabelElementIsDropped() {
        IssueQuery query = new IssueQuery();
        query.labels = new ArrayList<>(Arrays.asList("ready-for-agent", null));
        assertThat(query.normalizedLabels()).containsExactly("ready-for-agent");
    }

    // ── Sort parameters: binding, defaults and the whitelist ─────────────

    private static IssueQuery queryWith(String sort, String direction, String childDirection) {
        IssueSearchParams params = new IssueSearchParams();
        params.sort = sort;
        params.direction = direction;
        params.childDirection = childDirection;
        return IssueQuery.fromSearchParams(params);
    }

    /** Omitted parameters mean the documented defaults, both parent- and child-level. */
    @Test
    void omittedSortParametersMeanTheDocumentedDefaults() {
        IssueQuery query = queryWith(null, null, null);
        assertThat(query.defaultOrder()).isEqualTo(IssueOrder.NEWEST_CREATED_FIRST);
        assertThat(query.childOrder()).isEqualTo(IssueOrder.OLDEST_CREATED_FIRST);
    }

    /** Each whitelisted field binds; direction without sort still applies to the createdAt default. */
    @Test
    void whitelistedSortFieldAndDirectionBind() {
        for (IssueOrder.Field field : IssueOrder.Field.values()) {
            IssueQuery query = queryWith(field.property, "asc", null);
            assertThat(query.defaultOrder()).isEqualTo(IssueOrder.of(field, IssueOrder.Direction.ASC));
        }
        assertThat(queryWith(null, "asc", null).defaultOrder())
                .isEqualTo(IssueOrder.of(IssueOrder.Field.CREATED_AT, IssueOrder.Direction.ASC));
        assertThat(queryWith(null, "desc", null).defaultOrder())
                .isEqualTo(IssueOrder.NEWEST_CREATED_FIRST);
    }

    /** child_direction re-orders children wherever they are served, defaulting oldest-first. */
    @Test
    void childDirectionBindsWithAnOldestFirstDefault() {
        assertThat(queryWith(null, null, "desc").childOrder())
                .isEqualTo(IssueOrder.NEWEST_CREATED_FIRST);
        assertThat(queryWith(null, null, "asc").childOrder())
                .isEqualTo(IssueOrder.OLDEST_CREATED_FIRST);
    }

    /**
     * A query that names a parent lists that parent's children as their own
     * rows, so its sort/direction are silently ignored and only child_direction
     * orders it.
     */
    @Test
    void aParentListingIsOrderedByChildDirectionAlone() {
        IssueQuery query = queryWith("title", "asc", "desc");
        query.parentId = 7L;
        assertThat(query.defaultOrder()).isEqualTo(query.childOrder());
        assertThat(query.defaultOrder()).isEqualTo(IssueOrder.NEWEST_CREATED_FIRST);
    }

    /** A blank value carries no sort to apply — blank means absent, the same rule as labels and search. */
    @Test
    void blankSortValuesMeanAbsent() {
        IssueQuery query = queryWith("", "", "");
        assertThat(query.defaultOrder()).isEqualTo(IssueOrder.NEWEST_CREATED_FIRST);
        assertThat(query.childOrder()).isEqualTo(IssueOrder.OLDEST_CREATED_FIRST);
    }

    /** The whitelisted values are the contract: anything unrecognized is rejected, not silently defaulted. */
    @Test
    void unrecognizedSortValuesAreRejected() {
        assertThatThrownBy(() -> queryWith("size", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("createdAt, updatedAt, title, id");
        assertThatThrownBy(() -> queryWith(null, "newest", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("asc, desc");
        assertThatThrownBy(() -> queryWith(null, null, "newest"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("asc, desc");
    }

    /** The detail endpoint's child_direction parses through the same default and whitelist. */
    @Test
    void theDetailChildDirectionParamParsesToTheChildOrder() {
        assertThat(IssueQuery.childOrderFromParam(null)).isEqualTo(IssueOrder.OLDEST_CREATED_FIRST);
        assertThat(IssueQuery.childOrderFromParam("")).isEqualTo(IssueOrder.OLDEST_CREATED_FIRST);
        assertThat(IssueQuery.childOrderFromParam("asc")).isEqualTo(IssueOrder.OLDEST_CREATED_FIRST);
        assertThat(IssueQuery.childOrderFromParam("desc")).isEqualTo(IssueOrder.NEWEST_CREATED_FIRST);
        assertThatThrownBy(() -> IssueQuery.childOrderFromParam("newest"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("asc, desc");
    }

    /** A pinned order still overrides the default — the frontier's oldest-first pin rides on this. */
    @Test
    void aPinnedOrderOverridesTheSortDefaults() {
        IssueQuery query = queryWith("title", "asc", "desc");
        query.pinnedOrder = IssueOrder.OLDEST_CREATED_FIRST;
        assertThat(query.effectiveOrder()).isEqualTo(IssueOrder.OLDEST_CREATED_FIRST);
    }
}
