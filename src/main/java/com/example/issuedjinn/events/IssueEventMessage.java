package com.example.issuedjinn.events;

import java.time.Instant;
import java.util.List;

/**
 * One issue change as it goes on the wire — the JSON data payload of an SSE frame,
 * numbered by the broadcaster's per-process sequence. The {@code type} doubles as the
 * named SSE event ({@code event: issue_created}), and the SSE {@code id:} field
 * repeats the {@code seq}:
 *
 * <pre>{@code id:1
 * event:issue_created
 * data:{"type":"issue_created","seq":1,"issueIds":[42],"at":"2026-01-01T00:00:00Z"}}</pre>
 *
 * @param type     what happened (also the SSE event name)
 * @param seq      per-process monotonically increasing sequence number
 * @param issueIds the mutated issue first, then its counterparts
 * @param at       when the change happened
 */
public record IssueEventMessage(String type, long seq, List<Long> issueIds, Instant at) {
}