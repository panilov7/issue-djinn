// Client-side ranking for the shared issue picker's candidate list.
//
// Search itself is server-side (`GET /api/issues?search=…`), and the server
// orders its page most-recently-updated — right for a list, wrong for a
// picker. A picker is queried with intent, so the row the term names should
// come first. `rankCandidates` re-ranks a fetched page purely lexically: the
// id the term names, then title prefixes, then title substrings, then
// everything else, alphabetical within a tier. Status is deliberately not a
// dimension — status filtering is the server's job, and a closed id hit must
// not be demoted below an open title match.

import type { IssueListItem } from '$lib/types';

/** The rank tiers a candidate can land in, lowest value first. */
const ID_HIT = 0;
const TITLE_STARTS_WITH = 1;
const TITLE_CONTAINS = 2;
const UNRANKED = 3;

/**
 * The issue id a search term names, or null when it names none.
 *
 * Mirrors the backend's `IssueQuery.searchId()` so the picker ranks exactly
 * what the server matched: surrounding whitespace stripped, an optional
 * leading `#` stripped, and the remainder a positive integer. Mixed terms
 * like `36 fix`, non-positive numbers like `0`, and numbers too large to be
 * an id are plain text searches.
 */
export function parseIdTerm(term: string): number | null {
	const normalized = term.trim().replace(/^#/, '');
	if (!/^\d+$/.test(normalized)) return null;

	const id = Number(normalized);
	return Number.isSafeInteger(id) && id > 0 ? id : null;
}

function rankOf(issue: IssueListItem, query: string, id: number | null): number {
	const title = issue.title.toLowerCase();
	if (id !== null && issue.id === id) return ID_HIT;
	if (title.startsWith(query)) return TITLE_STARTS_WITH;
	if (title.includes(query)) return TITLE_CONTAINS;
	// The light list shape carries no description, so a row the server matched
	// on description alone is indistinguishable from an unranked one.
	return UNRANKED;
}

/**
 * Re-rank a fetched page for the term it was fetched with.
 *
 * An empty (or blank) term ranks nothing — the page stays in the server's
 * most-recently-updated order. Returns a new array; the input is untouched.
 */
export function rankCandidates(issues: IssueListItem[], term: string): IssueListItem[] {
	const query = term.trim().toLowerCase();
	const id = parseIdTerm(term);
	if (!query && id === null) return [...issues];

	return [...issues].sort((a, b) => {
		const byTier = rankOf(a, query, id) - rankOf(b, query, id);
		return byTier !== 0 ? byTier : a.title.localeCompare(b.title);
	});
}
