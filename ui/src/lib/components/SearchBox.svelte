<script lang="ts">
	import { onMount, untrack } from 'svelte';
	import SearchIcon from '@lucide/svelte/icons/search';
	import XIcon from '@lucide/svelte/icons/x';
	import * as InputGroup from '$lib/components/ui/input-group';
	import { Button } from '$lib/components/ui/button';

	let {
		onsearch,
		placeholder = 'Search issues…',
		initial = ''
	}: {
		onsearch?: (value: string) => void;
		placeholder?: string;
		/** The text the box starts with — the search restored from storage. */
		initial?: string;
	} = $props();

	// One-shot seed: the box owns the live text afterwards.
	let value = $state(untrack(() => initial));
	let timer: ReturnType<typeof setTimeout> | undefined;
	let lastEmitted: string | undefined;

	// A keystroke still waiting to debounce must not land after the box is
	// gone: the filter bar re-creates the box on a filter reset, and the list
	// unmounts it on navigation — either way a late emit would filter a list
	// this box no longer backs.
	onMount(() => () => clearTimeout(timer));

	function handleInput(event: Event) {
		const next = (event.currentTarget as HTMLInputElement).value;
		value = next;
		clearTimeout(timer);
		// Clearing the box applies immediately; typing debounces by 300ms.
		if (next === '') {
			emit('');
			return;
		}
		timer = setTimeout(() => emit(next), 300);
	}

	function clear() {
		value = '';
		clearTimeout(timer);
		emit('');
	}

	function emit(next: string) {
		if (lastEmitted === next) return;
		lastEmitted = next;
		onsearch?.(next);
	}
</script>

<InputGroup.Root class="w-full max-w-xs">
	{#if value}
		<InputGroup.Addon>
			<Button variant="ghost" size="sm" class="px-1" aria-label="Clear search" onclick={clear}>
				<XIcon data-icon="inline-start" />
			</Button>
		</InputGroup.Addon>
	{:else}
		<InputGroup.Addon>
			<SearchIcon data-icon="inline-start" />
		</InputGroup.Addon>
	{/if}
	<InputGroup.Input
		type="search"
		{placeholder}
		bind:value
		oninput={handleInput}
		aria-label={placeholder}
	/>
</InputGroup.Root>
