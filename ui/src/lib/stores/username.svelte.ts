import { browser } from '$app/environment';

const KEY = 'issue-djinn.username';

// Shared singleton holding the user's display name.
// The UI's source for the default comment author and claim assignee.
class UsernameStore {
	current = $state<string | null>(null);

	constructor() {
		if (browser) this.current = localStorage.getItem(KEY);
	}

	set(name: string) {
		const trimmed = name.trim();
		this.current = trimmed || null;
		if (browser) {
			if (trimmed) localStorage.setItem(KEY, trimmed);
			else localStorage.removeItem(KEY);
		}
	}

	clear() {
		this.current = null;
		if (browser) localStorage.removeItem(KEY);
	}
}

export const username = new UsernameStore();
