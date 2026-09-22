import { describe, expect, it, vi } from 'vitest';
import { IssueEventStore } from './issueEvents.svelte';
import type { IssueChangeEvent, IssueChangeType } from '$lib/types';

// Minimal EventSource fake: records what the store subscribed to so a test
// can push frames and (re)connects through the real wiring.
class FakeEventSource {
	url: string;
	onopen: (() => void) | null = null;
	#listeners = new Map<string, Array<(event: MessageEvent) => void>>();

	constructor(url: string) {
		this.url = url;
	}

	addEventListener(type: string, listener: (event: MessageEvent) => void): void {
		const listeners = this.#listeners.get(type) ?? [];
		listeners.push(listener);
		this.#listeners.set(type, listeners);
	}

	open(): void {
		this.onopen?.();
	}

	emit(type: string, payload: unknown): void {
		this.emitRaw(type, JSON.stringify(payload));
	}

	emitRaw(type: string, data: string): void {
		for (const listener of this.#listeners.get(type) ?? []) listener({ data } as MessageEvent);
	}
}

function changeEvent(
	seq: number,
	issueIds: number[],
	type: IssueChangeType = 'issue_updated'
): IssueChangeEvent {
	return { type, seq, issueIds, at: '2026-09-13T10:00:00Z' };
}

function connectFake(store: IssueEventStore): FakeEventSource {
	const source = new FakeEventSource('/api/events');
	store.connect(() => source);
	return source;
}

describe('IssueEventStore', () => {
	it('opens one EventSource against /api/events through the injected factory', () => {
		const store = new IssueEventStore();
		const sources: FakeEventSource[] = [];
		store.connect((url) => {
			const source = new FakeEventSource(url);
			sources.push(source);
			return source;
		});

		expect(sources).toHaveLength(1);
		expect(sources[0].url).toBe('/api/events');
	});

	it('never opens a second connection, however often connect is called', () => {
		// One connection per tab: the root layout calls connect on mount, pages
		// never do, and the store makes a stray extra call harmless.
		const store = new IssueEventStore();
		const factory = vi.fn(() => new FakeEventSource('/api/events'));
		store.connect(factory);
		store.connect(factory);

		expect(factory).toHaveBeenCalledTimes(1);
	});

	it('delivers a named frame to listeners as a parsed change event and holds it as latestEvent', () => {
		const store = new IssueEventStore();
		const source = connectFake(store);
		const received: IssueChangeEvent[] = [];
		store.onEvent((event) => received.push(event));

		source.emit('issue_created', changeEvent(1, [42], 'issue_created'));

		expect(received).toEqual([changeEvent(1, [42], 'issue_created')]);
		expect(store.latestEvent).toEqual(changeEvent(1, [42], 'issue_created'));
	});

	it('keeps delivering later frames to the same listeners', () => {
		const store = new IssueEventStore();
		const source = connectFake(store);
		const received: IssueChangeEvent[] = [];
		store.onEvent((event) => received.push(event));

		source.emit('issue_created', changeEvent(1, [42], 'issue_created'));
		source.emit('issue_updated', changeEvent(2, [42]));

		expect(received).toEqual([changeEvent(1, [42], 'issue_created'), changeEvent(2, [42])]);
	});

	it('ignores a frame whose data is not valid JSON', () => {
		const store = new IssueEventStore();
		const source = connectFake(store);
		const received: IssueChangeEvent[] = [];
		store.onEvent((event) => received.push(event));

		source.emitRaw('issue_updated', 'not-json');

		expect(received).toEqual([]);
		expect(store.latestEvent).toBeNull();
	});

	it('notifies open listeners on every open — first connect and each reconnect', () => {
		const store = new IssueEventStore();
		const source = connectFake(store);
		const opens = vi.fn();
		store.onOpen(opens);

		source.open();
		source.open();

		expect(opens).toHaveBeenCalledTimes(2);
	});

	it('stops delivering after unsubscribe', () => {
		const store = new IssueEventStore();
		const source = connectFake(store);
		const received: IssueChangeEvent[] = [];
		const opens = vi.fn();
		const stopEvents = store.onEvent((event) => received.push(event));
		const stopOpens = store.onOpen(opens);

		stopEvents();
		stopOpens();
		source.emit('issue_updated', changeEvent(1, [42]));
		source.open();

		expect(received).toEqual([]);
		expect(opens).not.toHaveBeenCalled();
		// The store's own reactive state keeps tracking the stream either way.
		expect(store.latestEvent).toEqual(changeEvent(1, [42]));
	});
});