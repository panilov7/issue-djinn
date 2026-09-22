<script lang="ts">
	import ArrowDownIcon from '@lucide/svelte/icons/arrow-down';
	import ArrowUpIcon from '@lucide/svelte/icons/arrow-up';
	import { Button } from '$lib/components/ui/button';
	import type { SortDirection } from '$lib/types';

	let {
		value,
		onchange
	}: {
		value: SortDirection;
		onchange?: (direction: SortDirection) => void;
	} = $props();

	// One compact flip button instead of a two-item toggle group: every click
	// reverses the direction, so the persisted-snapshot no-op guard of the
	// toggle-group grammar has nothing to guard here.
	const ascending = $derived(value === 'asc');
</script>

<!-- Fixed width, sized so the wider desc spelling plus its arrow still fit
	(with the tighter px-2 padding), keeping neighbours on the sorting row
	put when the order flips. -->
<Button
	variant="outline"
	size="sm"
	class="w-[3.8rem] px-2"
	onclick={() => onchange?.(ascending ? 'desc' : 'asc')}
	aria-label={ascending ? 'Parent issue order: ascending' : 'Parent issue order: descending'}
>
	<span class="text-muted-foreground">{ascending ? 'asc' : 'desc'}</span>
	{#if ascending}
		<ArrowUpIcon class="size-3.5 text-muted-foreground" />
	{:else}
		<ArrowDownIcon class="size-3.5 text-muted-foreground" />
	{/if}
</Button>