import { createPersistedStore } from '$lib/stores/persisted.svelte';
import { isSortDirection } from '$lib/utils/issueFilters';
import type { SortDirection } from '$lib/types';

const KEY = 'issue-djinn.child-direction';

/** Validates a stored direction — anything off the `asc|desc` whitelist keeps the store's default. */
export function parseChildDirection(raw: unknown): SortDirection | undefined {
	return isSortDirection(raw) ? raw : undefined;
}

/**
 * The issue detail page's persisted child direction — its own memento,
 * deliberately separate from the list page's filter snapshot so the two
 * pages' order choices don't fight (per-page preferences).
 *
 * The load resolves the direction to serve: an explicit `?child_direction=`
 * link param (what the list page's "...and N more" link carries when its
 * embeds run flipped) overrides the memento and
 * is adopted as the choice, so the detail's children list starts where the
 * embed preview left off; any other visit reads the memento. The children
 * card's toggle writes its flips through {@link storeChildDirection}.
 */
export function resolveChildDirection(linked: string | null): SortDirection {
	const store = createPersistedStore<SortDirection>(KEY, 'asc', parseChildDirection);
	const linkedDirection = parseChildDirection(linked);
	if (linkedDirection) store.set(linkedDirection);
	return store.current;
}

/** Persists the children card's direction choice, so the next load serves it. */
export function storeChildDirection(direction: SortDirection): void {
	createPersistedStore<SortDirection>(KEY, 'asc', parseChildDirection).set(direction);
}