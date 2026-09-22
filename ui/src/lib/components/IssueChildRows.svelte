<script lang="ts">
	import ChevronRightIcon from '@lucide/svelte/icons/chevron-right';
	import type { IssueListItem, SortDirection } from '$lib/types';
	import IssueCard from './IssueCard.svelte';

	let {
		root,
		childDirection = 'asc'
	}: {
		/** The matching children riding along under a root row, plus the overflow past the cap. */
		root: IssueListItem;
		/** The direction this listing's embedded children run in. */
		childDirection?: SortDirection;
	} = $props();

	// A row embeds at most the first ten matching children; this is how many the
	// cap hid, and the overflow row is the way to them — the root's detail page.
	const hidden = $derived(root.matchingChildCount - root.children.length);
	// When the embeds run flipped, the link carries
	// the order along so the detail page starts where the preview left off; at
	// the default the URL stays bare, as before.
	const overflowHref = $derived(
		childDirection === 'desc' ? `/issues/${root.id}?child_direction=desc` : `/issues/${root.id}`
	);
</script>

{#each root.children as child (child.id)}
	<IssueCard issue={child} indent />
{/each}
{#if hidden > 0}
	<a
		href={overflowHref}
		class="flex items-center gap-1 py-2 pl-14 pr-4 text-sm text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
	>
		...and {hidden} more
		<ChevronRightIcon class="size-4 shrink-0" />
	</a>
{/if}
