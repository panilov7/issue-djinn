<script lang="ts">
	import RotateCcwIcon from '@lucide/svelte/icons/rotate-ccw';
	import { buttonVariants } from '$lib/components/ui/button';
	import * as Tooltip from '$lib/components/ui/tooltip';
	import SearchBox from './SearchBox.svelte';

	let {
		search,
		atDefault,
		onsearch,
		onreset
	}: {
		/** The applied search text, restored from storage on load. */
		search?: string;
		/** Whether the filter and sort state equals the defaults — the reset's disabled state. */
		atDefault: boolean;
		onsearch?: (text: string) => void;
		/** Apply the default filter and sort state — the reset button. */
		onreset?: () => void;
	} = $props();

	// Pinned in the filter bar's test: the reset's one line of help.
	const RESET_TOOLTIP = 'Reset the filters and sort order to the default frontier view.';

	// The search box seeds once from the applied search and owns its live text
	// after that, so a reset re-seeds it by re-creating it — by then the parent
	// has applied the default search, which is what the fresh seed shows.
	let searchResetSeq = $state(0);

	function handleReset() {
		searchResetSeq++;
		onreset?.();
	}
</script>

<div class="ml-auto flex items-center gap-2">
	{#key searchResetSeq}
		<SearchBox initial={search} onsearch={onsearch} />
	{/key}
	<Tooltip.Root>
		<Tooltip.Trigger
			class={buttonVariants({ variant: 'outline', size: 'icon-sm' })}
			onclick={handleReset}
			disabled={atDefault}
			aria-label="Reset filters"
		>
			<RotateCcwIcon />
		</Tooltip.Trigger>
		<Tooltip.Content>{RESET_TOOLTIP}</Tooltip.Content>
	</Tooltip.Root>
</div>