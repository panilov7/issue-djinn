<script lang="ts">
	import ChevronLeftIcon from '@lucide/svelte/icons/chevron-left';
	import ChevronRightIcon from '@lucide/svelte/icons/chevron-right';
	import { Button } from '$lib/components/ui/button';

	let {
		currentPage,
		hasNext,
		disabled = false,
		onchange
	}: {
		currentPage: number;
		hasNext: boolean;
		disabled?: boolean;
		onchange?: (page: number) => void;
	} = $props();
</script>

{#if currentPage > 1 || hasNext}
	<nav aria-label="pagination" class="flex items-center justify-center gap-3">
		<Button
			variant="outline"
			size="sm"
			disabled={disabled || currentPage <= 1}
			onclick={() => onchange?.(currentPage - 1)}
		>
			<ChevronLeftIcon data-icon="inline-start" />
			Previous
		</Button>
		<span class="text-sm text-muted-foreground">Page {currentPage}</span>
		<Button
			variant="outline"
			size="sm"
			disabled={disabled || !hasNext}
			onclick={() => onchange?.(currentPage + 1)}
		>
			Next
			<ChevronRightIcon data-icon="inline-end" />
		</Button>
	</nav>
{/if}
