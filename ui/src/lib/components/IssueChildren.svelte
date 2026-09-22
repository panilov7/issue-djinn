<script lang="ts">
	import PlusIcon from '@lucide/svelte/icons/plus';
	import { buttonVariants } from '$lib/components/ui/button';
	import * as Card from '$lib/components/ui/card';
	import { EditingGate } from '$lib/stores/editingGate.svelte';
	import type { IssueDetail, SortDirection } from '$lib/types';
	import ChildDirectionToggle from './ChildDirectionToggle.svelte';
	import CreateIssueDialog from './CreateIssueDialog.svelte';
	import StatusBadge from './StatusBadge.svelte';

	let {
		issue,
		onChildCreated,
		gate,
		childDirection = 'asc',
		onchilddirectionchange
	}: {
		/** The issue whose children this section lists. */
		issue: IssueDetail;
		/** Called after a child is created, so the page can refresh its data. */
		onChildCreated?: () => Promise<void> | void;
		/** The page's editing gate, passed to the child-issue dialog. */
		gate?: EditingGate;
		/** The direction the detail payload's children arrived in. */
		childDirection?: SortDirection;
		/** Flip the child order — the page refetches with the flipped `child_direction`. */
		onchilddirectionchange?: (direction: SortDirection) => void;
	} = $props();

	const children = $derived(issue.children);
	// A child cannot have children of its own (CONTEXT.md → Child), so only a
	// top-level issue offers the button. The depth rule in the service is the
	// backstop for direct API callers.
	const canAddChild = $derived(issue.parentId === null);
</script>

<Card.Root>
	<Card.Header>
		<Card.Title>Children <span class="text-muted-foreground">({children.length})</span></Card.Title>
		<Card.Action class="flex items-center gap-2">
			<ChildDirectionToggle value={childDirection} onchange={onchilddirectionchange} />
			{#if canAddChild}
				<CreateIssueDialog
					presetParent={issue}
					onCreated={onChildCreated}
					{gate}
					triggerClass={buttonVariants({ variant: 'outline', size: 'sm' })}
				>
					<PlusIcon data-icon="inline-start" />
					Add child issue
				</CreateIssueDialog>
			{/if}
		</Card.Action>
	</Card.Header>
	<Card.Content>
		{#if children.length === 0}
			<p class="text-sm text-muted-foreground">No child issues.</p>
		{:else}
			<ul class="flex flex-col gap-1.5">
				{#each children as child (child.id)}
					<li>
						<a
							class="flex min-w-0 items-center gap-2 text-sm hover:underline"
							href={`/issues/${child.id}`}
						>
							<span class="font-mono text-muted-foreground">#{child.id}</span>
							<span class="truncate">{child.title}</span>
							<StatusBadge status={child.status} />
						</a>
					</li>
				{/each}
			</ul>
		{/if}
	</Card.Content>
</Card.Root>
