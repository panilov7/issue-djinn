<script lang="ts">
	import * as ToggleGroup from '$lib/components/ui/toggle-group';
	import * as Tooltip from '$lib/components/ui/tooltip';
	import { isWorkFilter } from '$lib/utils/issueFilters';
	import type { WorkFilter } from '$lib/types';

	let {
		value,
		onchange
	}: {
		/** The pressed work-state segment, if either; absent when deselected. */
		value?: WorkFilter;
		onchange?: (value?: WorkFilter) => void;
	} = $props();

	// Pinned in this component's test: each segment's one line of predicate
	// help, in domain terms. The frontier sentence mirrors the canonical
	// definition in CONTEXT.md ("no *open* dependencies, unclaimed") and the
	// MCP `list_frontier` description.
	const FRONTIER_TOOLTIP =
		'Open issues with no open dependencies and no assignee — the work you can start right now.';
	const IN_PROGRESS_TOOLTIP =
		'Open issues that are claimed and unblocked — the work already happening.';

	// The primitive emits '' when the pressed item is clicked again — that
	// deselect removes the work-state restriction, so it travels as undefined.
	function toWorkFilter(raw: string): WorkFilter | undefined {
		return isWorkFilter(raw) ? raw : undefined;
	}
</script>

<Tooltip.Provider>
	<ToggleGroup.Root
		type="single"
		{value}
		onValueChange={(v) => onchange?.(toWorkFilter(v))}
		variant="outline"
		size="sm"
		aria-label="Work state"
	>
		<Tooltip.Root>
			<Tooltip.Trigger>
				{#snippet child({ props })}
					<ToggleGroup.Item value="frontier" {...props}>Frontier</ToggleGroup.Item>
				{/snippet}
			</Tooltip.Trigger>
			<Tooltip.Content>{FRONTIER_TOOLTIP}</Tooltip.Content>
		</Tooltip.Root>
		<Tooltip.Root>
			<Tooltip.Trigger>
				{#snippet child({ props })}
					<ToggleGroup.Item value="inProgress" {...props}>In progress</ToggleGroup.Item>
				{/snippet}
			</Tooltip.Trigger>
			<Tooltip.Content>{IN_PROGRESS_TOOLTIP}</Tooltip.Content>
		</Tooltip.Root>
	</ToggleGroup.Root>
</Tooltip.Provider>