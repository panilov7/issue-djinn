package com.example.issuedjinn.events;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory fan-out of committed issue change events to every subscribed client —
 * the SSE endpoint subscribes once per connected browser ({@code GET /api/events}).
 * Naive broadcast: no backpressure handling beyond the Mutiny default buffer, no
 * replay buffer — a local, single-user tool.
 *
 * <p>The observer fires only after the transaction that produced the event committed
 * (AFTER_SUCCESS), so a rolled-back mutation emits nothing. Each event is stamped
 * with a per-process monotonically increasing sequence number as it is published;
 * the SSE endpoint carries that number in both the frame's {@code id:} field and the
 * payload's {@code seq}.
 */
@ApplicationScoped
public class IssueChangeBroadcaster {

    private final List<MultiEmitter<? super IssueEventMessage>> subscribers = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();

    /** Publishes the event once the transaction that produced it has committed. */
    void onIssueChangeCommitted(@Observes(during = TransactionPhase.AFTER_SUCCESS) IssueChangeEvent event) {
        publish(event);
    }

    /**
     * Stamp the event with the next sequence number and hand it to every subscriber.
     * Synchronized so a subscriber never sees an out-of-order pair of frames when two
     * transactions commit concurrently; the Mutiny emitter serializes the concurrent
     * {@code emit} calls themselves, but only this lock serializes their order.
     */
    synchronized void publish(IssueChangeEvent event) {
        IssueEventMessage message = new IssueEventMessage(
                event.type().wireName(), sequence.incrementAndGet(), event.issueIds(), event.at());
        for (MultiEmitter<? super IssueEventMessage> subscriber : subscribers) {
            subscriber.emit(message);
        }
    }

    /** One hot stream per subscriber; it ends when the subscriber disconnects. */
    public Multi<IssueEventMessage> subscribe() {
        return Multi.createFrom().emitter(emitter -> {
            AtomicBoolean terminated = new AtomicBoolean();
            // Registered before the emitter is listed, so a cancel racing the
            // registration can't strand it: a termination that fires early is
            // compensated right below, and any later one removes it.
            emitter.onTermination(() -> {
                terminated.set(true);
                subscribers.remove(emitter);
            });
            subscribers.add(emitter);
            if (terminated.get()) {
                subscribers.remove(emitter);
            }
        });
    }
}