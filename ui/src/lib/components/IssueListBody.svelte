<script lang="ts">
	import * as Card from '$lib/components/ui/card';
	import type { IssueListItem, SortDirection } from '$lib/types';
	import IssueCard from './IssueCard.svelte';
	import IssueChildRows from './IssueChildRows.svelte';
	import Pagination from './Pagination.svelte';

	let {
		issues,
		hasNext,
		page,
		loading,
		childDirection,
		onPageChange
	}: {
		issues: IssueListItem[];
		hasNext: boolean;
		page: number;
		loading: boolean;
		/** The direction the embedded children run in, carried on overflow links. */
		childDirection: SortDirection;
		onPageChange?: (page: number) => void;
	} = $props();
</script>

<Card.Root>
	<!-- Rows are flat siblings so the dividers run between every row — a root's
		children sit directly beneath it, separated the same way. -->
	<div class="divide-y">
		{#each issues as issue (issue.id)}
			<IssueCard {issue} childCounts={issue.childCounts} dimmed={!issue.matchesFilter} />
			<IssueChildRows root={issue} {childDirection} />
		{/each}
	</div>
</Card.Root>
<Pagination currentPage={page} {hasNext} disabled={loading} onchange={onPageChange} />
