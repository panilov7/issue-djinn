// The calm auto-refresh policy for a page driven by the issue event stream
// React to every change event — any of them may name data on
// screen — and to every stream (re)connect, so events missed during a
// disconnection gap self-heal without server-side replay. Bursts collapse:
// each arriving event re-arms one trailing window, so an agent closing five
// issues in a second produces a single `refresh` run instead of a storm.

import { issueEvents } from './issueEvents.svelte';

/** The trailing window a burst of change events collapses into. */
const AUTO_REFRESH_DEBOUNCE_MS = 300;

/**
 * Schedule one debounced run of `refresh` per burst of change events and per
 * stream (re)connect. The subscriptions and any pending timer are torn down
 * when the calling component unmounts, so a refresh never fires into a page
 * that is gone.
 */
export function autoRefreshOnIssueEvents(refresh: () => void): void {
	let timer: ReturnType<typeof setTimeout> | undefined;

	const schedule = () => {
		clearTimeout(timer);
		timer = setTimeout(() => void refresh(), AUTO_REFRESH_DEBOUNCE_MS);
	};

	$effect(() => {
		const stopEvents = issueEvents.onEvent(schedule);
		const stopOpens = issueEvents.onOpen(schedule);
		return () => {
			stopEvents();
			stopOpens();
			clearTimeout(timer);
		};
	});
}