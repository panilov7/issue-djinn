<script lang="ts">
	import ArrowUpDownIcon from '@lucide/svelte/icons/arrow-up-down';
	import { buttonVariants } from '$lib/components/ui/button';
	import * as DropdownMenu from '$lib/components/ui/dropdown-menu';
	import { isSortField } from '$lib/utils/issueFilters';
	import type { IssueSortField } from '$lib/types';

	let {
		sortField,
		onsortfieldchange
	}: {
		sortField: IssueSortField;
		onsortfieldchange?: (field: IssueSortField) => void;
	} = $props();

	// The field names users see; the values are the REST `sort` whitelist's
	// camelCase spellings, passed through verbatim.
	const FIELDS: Array<{ value: IssueSortField; label: string }> = [
		{ value: 'createdAt', label: 'Created' },
		{ value: 'updatedAt', label: 'Updated' },
		{ value: 'title', label: 'Title' },
		{ value: 'id', label: 'Id' }
	];

	const currentField = $derived(FIELDS.find((field) => field.value === sortField));

	// The primitives hand back untyped strings (and '' for a re-click of the
	// pressed toggle item); only whitelisted values travel on, so the listing's
	// sort state can never pick up a stray value.
	function pickField(raw: string) {
		if (isSortField(raw)) onsortfieldchange?.(raw);
	}
</script>

<DropdownMenu.Root>
	<DropdownMenu.Trigger class={buttonVariants({ variant: 'outline', size: 'sm' })}>
		<ArrowUpDownIcon data-icon="inline-start" />
		Sort
		<span class="text-muted-foreground">{currentField?.label}</span>
	</DropdownMenu.Trigger>
	<DropdownMenu.Content align="start" class="w-44">
		<!-- The menu carries fields only: the direction toggle lives beside it,
			always visible on the sorting row. -->
		<DropdownMenu.RadioGroup value={sortField} onValueChange={pickField}>
			{#each FIELDS as field (field.value)}
				<DropdownMenu.RadioItem value={field.value}>
					{field.label}
				</DropdownMenu.RadioItem>
			{/each}
		</DropdownMenu.RadioGroup>
	</DropdownMenu.Content>
</DropdownMenu.Root>