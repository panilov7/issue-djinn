<script lang="ts">
	import * as ToggleGroup from '$lib/components/ui/toggle-group';
	import { isSortDirection } from '$lib/utils/issueFilters';
	import type { SortDirection } from '$lib/types';

	let {
		value,
		onchange
	}: {
		value: SortDirection;
		onchange?: (direction: SortDirection) => void;
	} = $props();

	// The primitive emits '' when the pressed item is clicked again; a listing
	// always carries a direction, so that gesture is a no-op and only
	// whitelisted values travel on.
	function pickDirection(raw: string) {
		if (isSortDirection(raw)) onchange?.(raw);
	}
</script>

<ToggleGroup.Root
	type="single"
	{value}
	onValueChange={pickDirection}
	variant="outline"
	size="sm"
	aria-label="Child issue order"
>
	<ToggleGroup.Item value="asc">Oldest first</ToggleGroup.Item>
	<ToggleGroup.Item value="desc">Newest first</ToggleGroup.Item>
</ToggleGroup.Root>