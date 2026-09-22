import { describe, expect, it } from 'vitest';
import type { IssueListItem } from '$lib/types';
import { parseIdTerm, rankCandidates } from './candidateRank';

function issue(id: number, title: string): IssueListItem {
	return {
		id,
		title,
		status: 'open',
		assignee: null,
		labels: [],
		parentId: null,
		updatedAt: '',
		childCounts: { open: 0, closed: 0, total: 0 },
		matchesFilter: true,
		matchingChildCount: 0,
		children: []
	};
}

/** The titles of a ranked page, in rank order. */
function titles(issues: IssueListItem[], term: string): string[] {
	return rankCandidates(issues, term).map((i) => i.title);
}

describe('parseIdTerm', () => {
	it.each([
		['36', 36],
		['#36', 36],
		[' 36 ', 36],
		[' #36 ', 36],
		['007', 7]
	])('parses %s as id %i', (term, expected) => {
		expect(parseIdTerm(term)).toBe(expected);
	});

	it.each(['', '#', '0', '-3', '36 fix', 'fix 36', 'abc', '3.5'])(
		'leaves %s as a plain text search',
		(term) => {
			expect(parseIdTerm(term)).toBeNull();
		}
	);

	it('rejects a number too large to be an id', () => {
		expect(parseIdTerm('9'.repeat(20))).toBeNull();
	});
});

describe('rankCandidates', () => {
	it('preserves the server order for an empty term', () => {
		const page = [issue(1, 'Recently touched'), issue(2, 'Older')];

		expect(rankCandidates(page, '')).toEqual(page);
		expect(rankCandidates(page, '   ')).toEqual(page);
	});

	it('ranks the issue the term names by id first', () => {
		const page = [issue(1, 'Alpha'), issue(36, 'Beta'), issue(2, 'Gamma')];

		expect(titles(page, '36')).toEqual(['Beta', 'Alpha', 'Gamma']);
		expect(titles(page, '#36')).toEqual(['Beta', 'Alpha', 'Gamma']);
	});

	it('ranks title-starts-with above title-contains', () => {
		const page = [issue(1, 'Undo the label change'), issue(2, 'Label rendering')];

		expect(titles(page, 'label')).toEqual(['Label rendering', 'Undo the label change']);
	});

	it('ranks title-contains above a description-only match', () => {
		// The light list shape carries no description, so a row the server matched
		// on description alone is indistinguishable from an unranked one — it can
		// only land in the last tier.
		const page = [issue(1, 'Nothing alike'), issue(2, 'Combobox')];

		expect(titles(page, 'combobox')).toEqual(['Combobox', 'Nothing alike']);
	});

	it('alphabetises within a tier', () => {
		const page = [issue(1, 'Zebra striping'), issue(2, 'Alpha sort'), issue(3, 'Zebra crossing')];

		expect(titles(page, 'zebra')).toEqual(['Zebra crossing', 'Zebra striping', 'Alpha sort']);
	});

	it('matches titles case-insensitively', () => {
		const page = [issue(1, 'SQLITE locking'), issue(2, 'Other')];

		expect(titles(page, 'sqlite')).toEqual(['SQLITE locking', 'Other']);
	});

	it('never mutates the page it was handed', () => {
		const page = [issue(1, 'Beta'), issue(2, 'Alpha')];

		rankCandidates(page, 'a');

		expect(page.map((i) => i.title)).toEqual(['Beta', 'Alpha']);
	});

	it('keeps the id tier purely lexical — status never moves a row', () => {
		// A closed id hit still outranks an open title match.
		const page = [
			{ ...issue(1, 'Open lookalike'), status: 'open' as const },
			{ ...issue(36, 'Closed target'), status: 'closed' as const }
		];

		expect(titles(page, '#36')).toEqual(['Closed target', 'Open lookalike']);
	});
});
