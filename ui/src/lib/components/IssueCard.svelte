<script lang="ts">
	import type { ChildCounts, IssueSummary } from '$lib/types';
	import { cn } from '$lib/utils';
	import { relativeTime } from '$lib/utils/time';
	import IssueChildCounts from './IssueChildCounts.svelte';
	import StatusBadge from './StatusBadge.svelte';
	import LabelChip from './LabelChip.svelte';
	import InitialsAvatar from './InitialsAvatar.svelte';

	let {
		issue,
		childCounts = null,
		indent = false,
		dimmed = false
	}: {
		issue: IssueSummary;
		/** Present on grouped list roots: the first-level children line under the title. */
		childCounts?: ChildCounts | null;
		/** Nested one level under its root row. */
		indent?: boolean;
		/**
		 * A container row that misses the filters but is listed for a matching
		 * child — dimmed, with the reason on hover.
		 */
		dimmed?: boolean;
	} = $props();

	/** Why a dimmed row is on the page at all: it is the container of a matching child. */
	const dimmedReason = 'Does not match your filters — listed because one of its child issues does';
</script>

<a
	href={`/issues/${issue.id}`}
	class={cn(
		'flex items-center gap-4 px-4 py-3 transition-colors hover:bg-muted focus-visible:bg-muted',
		indent && 'pl-14',
		dimmed && 'opacity-60'
	)}
	title={dimmed ? dimmedReason : undefined}
>
	<span class="w-10 shrink-0 font-mono text-sm tabular-nums text-muted-foreground">
		#{issue.id}
	</span>
	<span class="min-w-0 flex-1">
		<span class="block truncate font-medium">{issue.title}</span>
		{#if childCounts && childCounts.total > 0}
			<!-- The counts answer "how much work sits under this root?", so a
				childless row has nothing to say and renders no line at all. -->
			<IssueChildCounts counts={childCounts} />
		{/if}
	</span>
	{#if issue.labels.length > 0}
		<span class="hidden shrink-0 items-center gap-1.5 sm:flex">
			{#each issue.labels as label (label)}
				<LabelChip {label} />
			{/each}
		</span>
	{/if}
	<InitialsAvatar name={issue.assignee} />
	<StatusBadge status={issue.status} title={dimmed ? dimmedReason : undefined} />
	<span class="w-24 shrink-0 text-right text-sm text-muted-foreground">
		{relativeTime(issue.updatedAt)}
	</span>
</a>
