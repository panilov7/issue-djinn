// The editing gate: the detail page's surfaces holding unsaved
// work — the edit form, the close/reopen dialog, the comment composer, the
// child-issue dialog — report here while they are open. While the gate holds,
// the page defers event-driven refreshes and shows its "changes available"
// indicator instead; the deferred refresh runs when the last surface closes.

import { SvelteSet } from 'svelte/reactivity';

/** The detail page's surfaces that can hold unsaved work. */
export type GatedSurface =
	| 'edit-form'
	| 'close-reopen-dialog'
	| 'comment-composer'
	| 'create-issue-dialog';

/**
 * Counts the surfaces currently holding unsaved work on one page. Surfaces
 * report their open state by name, so registration is idempotent and a
 * surface cannot leak a stale hold by reporting again.
 */
export class EditingGate {
	// A SvelteSet, not `$state(new Set())`: only the former makes add/delete
	// reactive, and the page's deferred-refresh flush reads on it.
	#surfaces = new SvelteSet<GatedSurface>();

	/** True while any surface on this page holds unsaved work. */
	get holdingUnsavedWork(): boolean {
		return this.#surfaces.size > 0;
	}

	/**
	 * Hold the gate while `holding` is true — read reactively, so the hold
	 * follows the surface's open state — and release it when the calling
	 * component unmounts. Call during component initialization.
	 */
	holdWhile(name: GatedSurface, holding: () => boolean): void {
		$effect(() => {
			if (holding()) {
				this.#surfaces.add(name);
			} else {
				this.#surfaces.delete(name);
			}
			return () => {
				this.#surfaces.delete(name);
			};
		});
	}
}