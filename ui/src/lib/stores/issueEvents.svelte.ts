// The tab's single SSE connection to `GET /api/events` and the shared state it
// feeds: the latest change event, plus subscriptions pages react
// with. The connection is opened once from the root layout — never per page —
// and `connect` is idempotent, so a stray extra call cannot open a second one.
//
// The `EventSource` is injectable as a factory: production binds the browser's
// native constructor, tests bind a fake and push frames through the real
// wiring. The native `EventSource` reconnects on its own, and every open — the
// first connect and each automatic reconnect — reaches the `onOpen`
// subscribers, so a page can re-sync the events missed during a gap without
// any server-side replay.

import type { IssueChangeEvent, IssueChangeType } from '$lib/types';

/** The SSE endpoint the stream connects to. */
const EVENTS_URL = '/api/events';

/** The named events the stream carries (the `IssueChangeType` wire vocabulary). */
const EVENT_NAMES: readonly IssueChangeType[] = ['issue_created', 'issue_updated', 'issue_deleted'];

/**
 * The slice of the `EventSource` API the store consumes — the seam tests fake.
 * The DOM type is deliberately wider (`readyState`, `onmessage`, …); depending
 * on only what the stream needs keeps a fake down to these two members.
 */
export interface EventSourceStream {
	/** Fired on the initial connect and on every automatic reconnect. */
	onopen: ((event: Event) => void) | null;
	addEventListener(type: string, listener: (event: MessageEvent) => void): void;
}

export type EventSourceFactory = (url: string) => EventSourceStream;

type ChangeListener = (event: IssueChangeEvent) => void;

export class IssueEventStore {
	/** The most recent change event the stream delivered. */
	latestEvent = $state<IssueChangeEvent | null>(null);

	#source: EventSourceStream | null = null;
	#changeListeners: ChangeListener[] = [];
	#openListeners: (() => void)[] = [];

	/** Open the tab's one EventSource connection; later calls are no-ops. */
	connect(createSource: EventSourceFactory): void {
		if (this.#source) return;
		this.#source = createSource(EVENTS_URL);
		this.#source.onopen = () => {
			for (const listener of this.#openListeners) listener();
		};
		for (const name of EVENT_NAMES) {
			// A named SSE frame arrives as a MessageEvent carrying its JSON payload.
			this.#source.addEventListener(name, (event) => this.#receive(event as MessageEvent<string>));
		}
	}

	/** Call `listener` for every incoming change event; returns the unsubscribe function. */
	onEvent(listener: ChangeListener): () => void {
		this.#changeListeners.push(listener);
		return () => {
			this.#changeListeners = this.#changeListeners.filter((registered) => registered !== listener);
		};
	}

	/** Call `listener` on every (re)connect; returns the unsubscribe function. */
	onOpen(listener: () => void): () => void {
		this.#openListeners.push(listener);
		return () => {
			this.#openListeners = this.#openListeners.filter((registered) => registered !== listener);
		};
	}

	#receive(frame: MessageEvent<string>): void {
		try {
			const event = JSON.parse(frame.data) as IssueChangeEvent;
			this.latestEvent = event;
			for (const listener of this.#changeListeners) listener(event);
		} catch {
			// A frame that is not valid JSON carries no change; ignore it.
		}
	}
}

/** The app-wide store instance: one connection per tab, shared by every page. */
export const issueEvents = new IssueEventStore();