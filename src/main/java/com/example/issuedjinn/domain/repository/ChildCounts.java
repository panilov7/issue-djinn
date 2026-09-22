package com.example.issuedjinn.domain.repository;

/**
 * The child counts one issue's list row embeds: how many of its children are
 * open, how many are closed, and the two added up.
 *
 * <p>The counts answer "how much work sits under this issue?" — they are taken
 * over <em>all</em> first-level children (CONTEXT.md → Child), never narrowed by
 * whatever filter happened to produce the row, so they read the same on every
 * page a root appears on.
 *
 * <p>An issue with no children reports {@link #NONE} rather than no counts at
 * all, so the list row shape is the same for every row.
 */
public record ChildCounts(long open, long closed, long total) {

    /** The counts of an issue with no children. */
    public static final ChildCounts NONE = new ChildCounts(0, 0, 0);

    /**
     * These counts with {@code count} added to the bucket {@code status} names.
     * Status is CHECK-constrained to {@code open}/{@code closed}, so anything
     * that is not closed is open.
     */
    public ChildCounts adding(String status, long count) {
        return "closed".equals(status)
                ? new ChildCounts(open, closed + count, total + count)
                : new ChildCounts(open + count, closed, total + count);
    }
}