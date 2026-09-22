<script lang="ts">
	import { Button } from '$lib/components/ui/button';
	import { relativeTime } from '$lib/utils/time';
	import type { IssueDetail } from '$lib/types';
	import LabelChip from './LabelChip.svelte';
	import StatusBadge from './StatusBadge.svelte';

	let {
		issue,
		onEdit,
		onClose,
		onReopen,
		onClaim,
		onUnclaim
	}: {
		issue: IssueDetail;
		onEdit: () => void;
		onClose: () => void;
		onReopen: () => void;
		onClaim: () => void;
		onUnclaim: () => void;
	} = $props();
</script>

<div class="flex flex-col gap-3" data-slot="issue-header">
	<div class="flex flex-wrap items-center gap-3">
		<span class="font-mono text-sm text-muted-foreground">#{issue.id}</span>
		<h1 class="min-w-0 flex-1 text-2xl font-semibold tracking-tight">{issue.title}</h1>
		<StatusBadge status={issue.status} />
	</div>
	<div
		class="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-muted-foreground"
		data-slot="issue-meta"
	>
		<span>
			parent:
			{#if issue.parentId !== null}
				<a class="underline hover:text-foreground" href={`/issues/${issue.parentId}`}>#{issue.parentId}</a>
			{:else}
				— (top-level)
			{/if}
		</span>
		<span>assignee: {issue.assignee ?? '—'}</span>
		{#if issue.labels.length > 0}
			<span class="flex items-center gap-1.5">
				labels:
				{#each issue.labels as label (label)}
					<LabelChip {label} />
				{/each}
			</span>
		{/if}
		<span>updated {relativeTime(issue.updatedAt)} · created {relativeTime(issue.createdAt)}</span>
	</div>
	<div class="flex flex-wrap items-center gap-2">
		<Button variant="outline" size="sm" onclick={onEdit}>Edit</Button>
		{#if issue.status === 'open'}
			<Button variant="outline" size="sm" onclick={onClose}>Close issue</Button>
		{:else}
			<Button variant="outline" size="sm" onclick={onReopen}>Reopen</Button>
		{/if}
		{#if issue.assignee}
			<Button variant="ghost" size="sm" onclick={onUnclaim}>Unclaim</Button>
		{:else if issue.status === 'open'}
			<Button variant="ghost" size="sm" onclick={onClaim}>Claim</Button>
		{/if}
	</div>
</div>
