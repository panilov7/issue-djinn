package com.example.issuedjinn.domain.repository;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * One ordering rule for every issue listing: {@code ORDER BY <field> <direction>,
 * id <direction>} — the id tie-breaker always traveling with the direction (both
 * flip together), so two issues that tie on the sort field order identically on
 * every request instead of shuffling between loads.
 *
 * <p>The defaults the surfaces serve are fixed here:
 * parent-level listings are newest-created-first, child listings — grouped
 * embeds, the {@code ?parent=N} rows, detail children — are oldest-created-first.
 * The REST {@code sort}/{@code direction}/{@code child_direction} parameters
 * drive the same builder: {@link Field} and {@link Direction} are
 * their whitelists, and a caller-pinned order overrides the default (see
 * {@link IssueQuery#effectiveOrder()}). The order holds a {@link Field}, not the
 * raw parameter string — an order cannot exist off the whitelist, so nothing
 * unvalidated ever reaches an ORDER BY.
 */
public record IssueOrder(Field field, boolean ascending) {

    /** Parent-level listings: newest-created-first. */
    public static final IssueOrder NEWEST_CREATED_FIRST = of(Field.CREATED_AT, Direction.DESC);

    /** Child listings: oldest-created-first. */
    public static final IssueOrder OLDEST_CREATED_FIRST = of(Field.CREATED_AT, Direction.ASC);

    /**
     * The order of a REST {@code sort} + {@code direction} pair: the field the
     * caller named in the direction the caller asked for. The whitelist lives
     * on {@link Field}, the direction values on {@link Direction} — an
     * unrecognized value never reaches an ORDER BY.
     */
    public static IssueOrder of(Field field, Direction direction) {
        return new IssueOrder(field, direction.ascending);
    }

    /**
     * The order children are served in: always by {@code createdAt} (children
     * have no other sort field), in the given direction. Ascending
     * is the default oldest-created-first, descending its flip.
     */
    public static IssueOrder childOrder(Direction direction) {
        return direction == Direction.ASC ? OLDEST_CREATED_FIRST : NEWEST_CREATED_FIRST;
    }

    /**
     * The fields a listing may be ordered by — the REST {@code sort} whitelist.
     * The spellings are the entity's JPQL property names, which coincide with
     * the JSON camelCase spellings the sort values use.
     */
    public enum Field {
        CREATED_AT("createdAt"),
        UPDATED_AT("updatedAt"),
        TITLE("title"),
        ID("id");

        /** The entity property the field orders by, also its REST sort value. */
        public final String property;

        Field(String property) {
            this.property = property;
        }

        /**
         * The field a REST {@code sort} value names, rejecting anything off the
         * whitelist — the caller's client fails loudly (400) instead of
         * silently falling back to the default order. The accepted list in the
         * message is derived from the constants, so it cannot go stale.
         */
        public static Field fromValue(String value) {
            for (Field field : values()) {
                if (field.property.equals(value)) {
                    return field;
                }
            }
            throw new IllegalArgumentException("Unrecognized sort field: '" + value + "'. Accepted: "
                    + Arrays.stream(values()).map(field -> field.property)
                            .collect(Collectors.joining(", ")));
        }
    }

    /**
     * The directions a listing may run in — the REST {@code direction} and
     * {@code child_direction} whitelist.
     */
    public enum Direction {
        ASC(true),
        DESC(false);

        /** Whether the direction orders smallest/oldest first. */
        public final boolean ascending;

        Direction(boolean ascending) {
            this.ascending = ascending;
        }

        /**
         * The direction a REST value names, rejecting anything off the
         * whitelist — the caller's client fails loudly (400) instead of
         * silently falling back to the default direction. The accepted list in
         * the message is derived from the constants, so it cannot go stale.
         */
        public static Direction fromValue(String value) {
            for (Direction direction : values()) {
                if (direction.name().toLowerCase(Locale.ROOT).equals(value)) {
                    return direction;
                }
            }
            throw new IllegalArgumentException("Unrecognized direction: '" + value + "'. Accepted: "
                    + Arrays.stream(values()).map(direction -> direction.name().toLowerCase(Locale.ROOT))
                            .collect(Collectors.joining(", ")));
        }
    }

    /**
     * The JPQL {@code ORDER BY} fragment over the given entity alias, with the
     * id tie-breaker traveling with the direction — unless the sort field is
     * the id itself, which is its own tie-breaker and so needs no second term.
     */
    public String fragmentFor(String alias) {
        String direction = ascending ? "ASC" : "DESC";
        if (field == Field.ID) {
            return " ORDER BY " + alias + "." + field.property + " " + direction;
        }
        return " ORDER BY " + alias + "." + field.property + " " + direction
                + ", " + alias + ".id " + direction;
    }
}
