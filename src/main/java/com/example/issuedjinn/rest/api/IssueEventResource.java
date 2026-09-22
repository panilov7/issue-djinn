package com.example.issuedjinn.rest.api;

import com.example.issuedjinn.events.IssueChangeBroadcaster;
import com.example.issuedjinn.events.IssueEventMessage;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.time.Duration;

/**
 * SSE stream of issue change events ({@code GET /api/events}). A committed issue
 * creation is one named event ({@code issue_created}) whose {@code id:} field
 * repeats the payload's sequence number; heartbeats keep an otherwise idle
 * connection open. Events carry no entity bodies: a client that reconnects
 * re-syncs by refetching. The other mutation types join the stream as
 * they are emitted by {@code IssueService}.
 */
@Path("/events")
public class IssueEventResource {

    private static final String HEARTBEAT_COMMENT = "heartbeat";

    @Inject
    IssueChangeBroadcaster broadcaster;

    @Inject
    Sse sse;

    /** How often an idle connection receives a heartbeat SSE comment. */
    @ConfigProperty(name = "issue-djinn.events.heartbeat-interval", defaultValue = "15S")
    Duration heartbeatInterval;

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    // The event data is the payload record itself; this is what JSON-encodes it.
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<OutboundSseEvent> stream() {
        return Multi.createBy().merging().streams(
                broadcaster.subscribe().map(this::event),
                // Heartbeats are droppable: a stalled reader must not kill the stream.
                Multi.createFrom().ticks().every(heartbeatInterval).onOverflow().drop().map(tick -> heartbeat()));
    }

    /** The named event frame: {@code id} carries the sequence, the data is the JSON payload. */
    private OutboundSseEvent event(IssueEventMessage message) {
        return sse.newEventBuilder()
                .id(Long.toString(message.seq()))
                .name(message.type())
                .data(message)
                .build();
    }

    private OutboundSseEvent heartbeat() {
        return sse.newEventBuilder().comment(HEARTBEAT_COMMENT).build();
    }
}