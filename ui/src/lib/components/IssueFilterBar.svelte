<script lang="ts">
	import * as Tooltip from '$lib/components/ui/tooltip';
	import { isDefaultFilterSnapshot } from '$lib/utils/issueFilters';
	import type { IssueSortField, IssueStatusFilter, SortDirection, WorkFilter } from '$lib/types';
	import ChildDirectionToggle from './ChildDirectionToggle.svelte';
	import LabelFilter from './LabelFilter.svelte';
	import ParentDirectionToggle from './ParentDirectionToggle.svelte';
	import ParentSortMenu from './ParentSortMenu.svelte';
	import SearchReset from './SearchReset.svelte';
	import StatusToggleGroup from './StatusToggleGroup.svelte';
	import WorkStateToggleGroup from './WorkStateToggleGroup.svelte';

	let {
		status,
		workFilter,
		labels = [],
		selectedLabels = [],
		search = '',
		sortField,
		direction,
		childDirection,
		onstatuschange,
		onworkfilterchange,
		onlabelschange,
		onsearch,
		onsortfieldchange,
		ondirectionchange,
		onchilddirectionchange,
		onreset
	}: {
		status: IssueStatusFilter;
		/** The pressed work-state segment, if either; absent when deselected. */
		workFilter?: WorkFilter;
		labels: string[];
		selectedLabels: string[];
		/** The applied search text, restored from storage on load. */
		search?: string;
		/** The field parent rows are ordered by. */
		sortField: IssueSortField;
		/** The direction parent rows run in. */
		direction: SortDirection;
		/** The direction every embedded child row on the page runs in. */
		childDirection: SortDirection;
		onstatuschange?: (status: IssueStatusFilter) => void;
		onworkfilterchange?: (value?: WorkFilter) => void;
		onlabelschange?: (labels: string[]) => void;
		onsearch?: (text: string) => void;
		onsortfieldchange?: (field: IssueSortField) => void;
		ondirectionchange?: (direction: SortDirection) => void;
		onchilddirectionchange?: (direction: SortDirection) => void;
		/** Apply the default filter and sort state — the reset button. */
		onreset?: () => void;
	} = $props();

	// The reset is offered only while the filters or sort differ from the
	// defaults; pagination is not part of the snapshot, so it never counts.
	const atDefaultFilters = $derived(
		isDefaultFilterSnapshot({
			status,
			labels: selectedLabels,
			workFilter,
			search,
			sortField,
			direction,
			childDirection
		})
	);
</script>

<Tooltip.Provider>
	<!-- Two rows, one concern each: filters and search above, sorting below
		— the sort controls read apart from the filter controls. -->
	<div class="flex flex-col gap-2">
		<div class="flex flex-wrap items-center gap-2">
			<WorkStateToggleGroup value={workFilter} onchange={onworkfilterchange} />

			<StatusToggleGroup
				value={status}
				disabled={workFilter !== undefined}
				onchange={onstatuschange}
			/>

			<LabelFilter labels={labels} value={selectedLabels} onchange={onlabelschange} />

			<SearchReset {search} atDefault={atDefaultFilters} onsearch={onsearch} onreset={onreset} />
		</div>

		<div class="flex flex-wrap items-start gap-x-4 gap-y-2">
			<div class="flex flex-col gap-1">
				<span class="text-xs text-muted-foreground">Parent issues</span>
				<div class="flex items-center gap-2">
					<ParentSortMenu {sortField} onsortfieldchange={onsortfieldchange} />
					<ParentDirectionToggle value={direction} onchange={ondirectionchange} />
				</div>
			</div>

			<div class="flex flex-col gap-1">
				<span class="text-xs text-muted-foreground">Child issues</span>
				<ChildDirectionToggle value={childDirection} onchange={onchilddirectionchange} />
			</div>
		</div>
	</div>
</Tooltip.Provider>