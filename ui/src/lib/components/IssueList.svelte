<script lang="ts">
	import { onMount } from 'svelte';
	import * as Alert from '$lib/components/ui/alert';
	import { Button } from '$lib/components/ui/button';
	import * as Card from '$lib/components/ui/card';
	import { listIssues } from '$lib/api/issues';
	import { apiErrorMessage } from '$lib/api/client';
	import { autoRefreshOnIssueEvents } from '$lib/stores/issueAutoRefresh.svelte';
	import type {
		IssueListItem,
		IssueSortField,
		IssueStatusFilter,
		SortDirection,
		WorkFilter
	} from '$lib/types';
	import {
		buildIssueParams,
		filterSnapshot,
		initialFilters,
		type IssueFilters
	} from '$lib/utils/issueFilters';
	import { createFiltersStore } from '$lib/stores/filters.svelte';
	import IssueCardSkeleton from './IssueCardSkeleton.svelte';
	import IssueEmpty from './IssueEmpty.svelte';
	import IssueFilterBar from './IssueFilterBar.svelte';
	import IssueListBody from './IssueListBody.svelte';

	let { labels = [] }: { labels: string[] } = $props();

	// Component `$state` owns the filter UI and the fetched list; the static
	// label vocabulary comes from the `+page.ts` load function. The user's
	// filter and sort choices persist across reloads: the store hydrates at
	// component init — before the first fetch — and an effect below writes
	// storage back on every mutation. Pagination is not part of the snapshot,
	// so it stays at its defaults here.
	const persistedFilters = createFiltersStore();
	const restoredSnapshot = persistedFilters.current;
	const filters: IssueFilters = $state({
		...initialFilters(),
		...restoredSnapshot,
		// The work-state pin holds for a restored snapshot too (the same pin
		// selection applies): storage from outside the app's own writes can
		// never show a disabled status control contradicting the work state's
		// open query.
		...(restoredSnapshot.workFilter !== undefined ? { status: 'open' as const } : {})
	});
	let issues = $state<IssueListItem[]>([]);
	let hasNext = $state(false);
	let loading = $state(false);
	let error = $state<string | null>(null);

	let showSkeleton = $state(false);
	let skeletonTimer: ReturnType<typeof setTimeout> | undefined;

	// Monotonic guard: a stale in-flight response must never clobber newer data.
	let requestSeq = 0;

	async function refresh() {
		const seq = ++requestSeq;
		loading = true;
		error = null;
		clearTimeout(skeletonTimer);
		showSkeleton = false;
		skeletonTimer = setTimeout(() => (showSkeleton = true), 150);
		try {
			const params = buildIssueParams(filters);
			// Peek one extra row to detect a next page without a total from the API.
			params.limit = filters.pageSize + 1;
			const res = await listIssues(params);
			if (seq !== requestSeq) return;
			hasNext = res.issues.length > filters.pageSize;
			issues = res.issues.slice(0, filters.pageSize);
		} catch (e) {
			if (seq !== requestSeq) return;
			error = apiErrorMessage(e);
		} finally {
			if (seq === requestSeq) {
				clearTimeout(skeletonTimer);
				loading = false;
				showSkeleton = false;
			}
		}
	}

	// Every filter or sort change lands here: apply it, return to page 1, and
	// refetch — the one shape all the bar's controls share.
	function applyFilters(patch: Partial<IssueFilters>) {
		Object.assign(filters, patch);
		filters.page = 1;
		void refresh();
	}

	function handleStatusChange(status: IssueStatusFilter) {
		applyFilters({ status });
	}

	function handleWorkFilterChange(workFilter?: WorkFilter) {
		// A work-state view is always open; a deselect removes the work-state
		// restriction and re-enables the status control at its current value.
		applyFilters(workFilter !== undefined ? { workFilter, status: 'open' } : { workFilter: undefined });
	}

	function handleLabelsChange(labels: string[]) {
		applyFilters({ labels });
	}

	function handleSearch(text: string) {
		applyFilters({ search: text });
	}

	function handleSortFieldChange(sortField: IssueSortField) {
		applyFilters({ sortField });
	}

	function handleDirectionChange(direction: SortDirection) {
		applyFilters({ direction });
	}

	function handleChildDirectionChange(childDirection: SortDirection) {
		applyFilters({ childDirection });
	}

	function handlePageChange(page: number) {
		// Paging never resets the page or the filters — it is the page change.
		filters.page = page;
		void refresh();
	}

	function resetFilters() {
		// Back to the default frontier view on page 1; the persist effect below
		// overwrites the stored snapshot with the defaults.
		Object.assign(filters, initialFilters());
		void refresh();
	}

	// Persist the applied filters on every mutation. `search` reaches the
	// filter state only after SearchBox's 300ms debounce, so storage follows
	// the debounce without extra wiring; `page` is never read here, so paging
	// never writes.
	$effect(() => {
		persistedFilters.set(filterSnapshot(filters));
	});

	// Live refresh: every change event matters to the list — any
	// issue on it may be the one that changed — so each burst of frames, and
	// every stream (re)connect (events missed during a disconnection gap
	// self-heal without replay), collapses into one refresh of this list, the
	// active filters riding along because refresh() reads them as they are.
	autoRefreshOnIssueEvents(refresh);

	onMount(() => {
		void refresh();
	});
</script>

<div class="flex flex-col gap-4">
	<IssueFilterBar
		status={filters.status}
		workFilter={filters.workFilter}
		{labels}
		selectedLabels={filters.labels}
		search={filters.search}
		sortField={filters.sortField}
		direction={filters.direction}
		childDirection={filters.childDirection}
		onstatuschange={handleStatusChange}
		onworkfilterchange={handleWorkFilterChange}
		onlabelschange={handleLabelsChange}
		onsearch={handleSearch}
		onsortfieldchange={handleSortFieldChange}
		ondirectionchange={handleDirectionChange}
		onchilddirectionchange={handleChildDirectionChange}
		onreset={resetFilters}
	/>

	{#if error}
		<Alert.Root variant="destructive">
			<Alert.Title>Could not load issues</Alert.Title>
			<Alert.Description>{error}</Alert.Description>
			<Alert.Action>
				<Button variant="outline" size="sm" onclick={() => void refresh()}>Retry</Button>
			</Alert.Action>
		</Alert.Root>
	{:else if showSkeleton}
		<Card.Root>
			<div class="divide-y" aria-hidden="true">
				{#each Array.from({ length: 5 }, (_, i) => i) as i (i)}
					<IssueCardSkeleton />
				{/each}
			</div>
		</Card.Root>
	{:else if issues.length === 0}
		<IssueEmpty workFilter={filters.workFilter} />
	{:else}
		<IssueListBody
			issues={issues}
			hasNext={hasNext}
			page={filters.page}
			loading={loading}
			childDirection={filters.childDirection}
			onPageChange={handlePageChange}
		/>
	{/if}
</div>
