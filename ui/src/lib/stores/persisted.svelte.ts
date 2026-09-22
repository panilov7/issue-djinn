import { browser } from '$app/environment';

/**
 * A reactive value backed by a localStorage key, for user preferences that
 * survive a reload (the `issue-djinn.*` storage keys, after the username
 * store's pattern).
 *
 * Hydrates from storage once, at creation, and writes storage on every `set`.
 * `parse` validates the stored blob — returning `undefined`, or throwing
 * while parsing, silently keeps `initial`, so corrupt or wrong-shaped data
 * falls back to the defaults instead of breaking the reader.
 */
export function createPersistedStore<T>(
	key: string,
	initial: T,
	parse: (raw: unknown) => T | undefined
) {
	let current = $state(initial);

	if (browser) {
		const stored = localStorage.getItem(key);
		if (stored !== null) {
			try {
				const restored = parse(JSON.parse(stored));
				if (restored !== undefined) current = restored;
			} catch {
				// Malformed JSON: keep the initial value.
			}
		}
	}

	return {
		get current(): T {
			return current;
		},
		set(value: T) {
			current = value;
			if (browser) localStorage.setItem(key, JSON.stringify(value));
		}
	};
}
