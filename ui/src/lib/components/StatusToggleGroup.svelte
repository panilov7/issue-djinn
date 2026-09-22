<script lang="ts">
	import * as ToggleGroup from '$lib/components/ui/toggle-group';
	import type { IssueStatusFilter } from '$lib/types';

	let {
		value,
		disabled = false,
		onchange
	}: {
		value: IssueStatusFilter;
		disabled?: boolean;
		onchange?: (value: IssueStatusFilter) => void;
	} = $props();

	// The primitive emits '' when the pressed item is clicked again. 'all' is the
	// deselect fallback, so a deselect is a return to 'all' — never an empty value.
	function toStatusFilter(raw: string): IssueStatusFilter {
		return raw === 'open' || raw === 'closed' ? raw : 'all';
	}
</script>

<ToggleGroup.Root
	type="single"
	{value}
	onValueChange={(v) => onchange?.(toStatusFilter(v))}
	variant="outline"
	size="sm"
	{disabled}
>
	<ToggleGroup.Item value="all">All</ToggleGroup.Item>
	<ToggleGroup.Item value="open">Open</ToggleGroup.Item>
	<ToggleGroup.Item value="closed">Closed</ToggleGroup.Item>
</ToggleGroup.Root>