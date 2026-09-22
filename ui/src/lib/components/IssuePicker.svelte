<script lang="ts">
	import { tick } from 'svelte';
	import * as Alert from '$lib/components/ui/alert';
	import * as Command from '$lib/components/ui/command';
	import * as Popover from '$lib/components/ui/popover';
	import { Popover as PopoverPrimitive } from 'bits-ui';
	import { listIssues } from '$lib/api/issues';
	import type { IssueListItem, IssueListParams, IssueStatusFilter } from '$lib/types';
	import { rankCandidates } from '$lib/utils/candidateRank';
	import { Skeleton } from '$lib/components/ui/skeleton';
	import StatusBadge from './StatusBadge.svelte';
	import StatusToggleGroup from './StatusToggleGroup.svelte';

	/** How long a pause in typing before it becomes a search request. */
	const SEARCH_DEBOUNCE_MS = 300;
	/** How long a fetch waits before showing a loading state, so fast APIs don't flash. */
	const LOADING_DELAY_MS = 150;
	/** The placeholder rows the loading skeleton shows. */
	const LOADING_ROWS = [1, 2, 3];

	/**
	 * Shared issue-picker combobox (Popover + Command).
	 *
	 * Search and status filtering are server-side: opening the popover fetches
	 * the status-scoped page (most-recently-updated, default page size), typing
	 * debounces into a `search` query, and clearing it restores the unsearched
	 * page. The fetched page is re-ranked lexically for the term that produced
	 * it — see `rankCandidates`.
	 *
	 * `status` scopes what the picker offers, for its lifetime. `'all'` renders
	 * the All/Open/Closed filter and starts on All; any other status is the only
	 * one ever fetched and renders no filter.
	 *
	 * Content passed as the default slot is rendered inside the trigger button;
	 * style the button itself via `class`, and pass anything else (e.g. an
	 * `aria-label`) through the usual rest props. Keep the slot content
	 * non-interactive — the trigger is already the button.
	 */
	let {
		status,
		excludeIds = [],
		onSelect,
		class: triggerClass,
		children,
		...restProps
	}: {
		/** The statuses the picker offers. `'all'` means "any status, user-filtered". */
		status: IssueStatusFilter;
		/** Issue ids to exclude from the picker. */
		excludeIds?: number[];
		/** Called with the selected IssueListItem. */
		onSelect?: (issue: IssueListItem) => void;
		/** Classes for the trigger button, e.g. `buttonVariants({ variant: 'outline' })`. */
		class?: string;
		/** Trigger content. */
		children?: import('svelte').Snippet;
	} & Omit<PopoverPrimitive.TriggerProps, 'class' | 'children' | 'ref' | 'id'> = $props();

	let open = $state(false);
	let triggerRef = $state<HTMLButtonElement | null>(null);

	// The status the user has filtered to, or null while they haven't touched the
	// filter — meaning the picker's own `status` still governs. Deriving the
	// effective value keeps the rendered filter and every fetch in agreement, and
	// keeps a fixed status fixed without suppressing a Svelte warning to do it.
	let selectedStatus = $state<IssueStatusFilter | null>(null);
	let activeStatus = $derived(selectedStatus ?? status);

	// The server's page for the current status + search, already ranked for the
	// term that produced it.
	let rankedIssues = $state<IssueListItem[]>([]);
	let fetchState = $state<'idle' | 'loading' | 'error'>('idle');
	// Debounce: only show the loading state after 150ms to avoid a flash on fast APIs.
	let showLoading = $state(false);
	let loadingTimer: ReturnType<typeof setTimeout> | undefined = undefined;
	let searchTimer: ReturnType<typeof setTimeout> | undefined = undefined;
	let search = $state('');
	// Bumped by every fetch; a response from a superseded request is dropped.
	let requestId = 0;

	// Candidates = the fetched page minus excluded ids. Ranking already happened
	// when the page landed, so excluding rows never disturbs the order.
	let candidates = $derived(rankedIssues.filter((issue) => !excludeIds.includes(issue.id)));
	let showStatusFilter = $derived(status === 'all');

	async function fetchPage() {
		const id = ++requestId;
		fetchState = 'loading';
		startLoadingTimer();

		// The term goes over verbatim — the backend strips surrounding whitespace
		// before its id parse, and trimming here would change what a text search
		// matches (see CONTEXT.md, "`search` query"). `flat` keeps every issue a
		// row of its own: the grouped list caps embedded children at ten, which
		// would silently hide pickable issues.
		const params: IssueListParams = { status: activeStatus, flat: true };
		if (search) params.search = search;

		try {
			const result = await listIssues(params);
			if (id !== requestId) return; // a newer fetch superseded this one
			rankedIssues = rankCandidates(result.issues, search);
			fetchState = 'idle';
		} catch {
			if (id !== requestId) return;
			rankedIssues = [];
			fetchState = 'error';
		} finally {
			if (id === requestId) stopLoadingTimer();
		}
	}

	function startLoadingTimer() {
		stopLoadingTimer();
		showLoading = false;
		loadingTimer = setTimeout(() => (showLoading = true), LOADING_DELAY_MS);
	}

	function stopLoadingTimer() {
		clearTimeout(loadingTimer);
		loadingTimer = undefined;
	}

	function handleSearchInput(event: Event) {
		search = (event.currentTarget as HTMLInputElement).value;
		clearTimeout(searchTimer);
		// Clearing applies immediately; typing waits for the pause — the
		// dashboard SearchBox idiom.
		if (search === '') {
			void fetchPage();
			return;
		}
		searchTimer = setTimeout(() => void fetchPage(), SEARCH_DEBOUNCE_MS);
	}

	function handleStatusChange(next: IssueStatusFilter) {
		if (next === activeStatus) return;
		selectedStatus = next;
		// The status is a refetch, not a keystroke — no debounce.
		clearTimeout(searchTimer);
		void fetchPage();
	}

	function handleOpenChange(isOpen: boolean) {
		open = isOpen;
		// A search still pending when the picker closes has nowhere to land.
		clearTimeout(searchTimer);
		if (!isOpen) return;
		// Reset so each open feels fresh: All pressed, no search, then fetch the
		// unsearched page. `fetchPage` resets the loading state itself.
		rankedIssues = [];
		search = '';
		selectedStatus = null;
		void fetchPage();
	}

	async function closeAndFocusTrigger() {
		open = false;
		await tick();
		triggerRef?.focus();
	}

	function handleSelect(issue: IssueListItem) {
		onSelect?.(issue);
		closeAndFocusTrigger();
	}
</script>

<Popover.Root bind:open onOpenChange={handleOpenChange}>
	<Popover.Trigger bind:ref={triggerRef} class={triggerClass} {...restProps}>
		{@render children?.()}
	</Popover.Trigger>
	<Popover.Content align="start" class="w-72 p-0">
		<Command.Root shouldFilter={false}>
			<Command.Input
				placeholder="Search by title or #id…"
				bind:value={search}
				oninput={handleSearchInput}
			/>
			{#if showStatusFilter}
				<div class="border-b px-3 py-2">
					<StatusToggleGroup value={activeStatus} onchange={handleStatusChange} />
				</div>
			{/if}
			<Command.List>
				{#if showLoading}
					<Command.Loading>
						<!-- Shape matches a candidate row, so the list doesn't jump when the page lands. -->
						<div class="flex flex-col gap-1 p-1" data-slot="issue-picker-loading">
							{#each LOADING_ROWS as row (row)}
								<div
									class="flex items-center gap-2 px-2 py-1.5"
									aria-hidden="true"
									data-testid="loading-row"
								>
									<Skeleton class="h-5 w-12 rounded-4xl" />
									<Skeleton class="h-4 flex-1" />
									<Skeleton class="h-4 w-8 shrink-0" />
								</div>
							{/each}
						</div>
					</Command.Loading>
				{:else if fetchState === 'error'}
					<Alert.Root variant="destructive">
						<Alert.Title>Could not load issues</Alert.Title>
						<Alert.Description>Reopen the picker to try again.</Alert.Description>
					</Alert.Root>
				{:else if candidates.length === 0}
					<Command.Empty>No issues found.</Command.Empty>
				{:else}
					<Command.Group>
						{#each candidates as issue (issue.id)}
							<Command.Item
								value={String(issue.id)}
								onSelect={() => handleSelect(issue)}
							>
								<StatusBadge status={issue.status} />
								<span class="truncate">{issue.title}</span>
								<span class="ms-auto shrink-0 font-mono text-muted-foreground">
									#{issue.id}
								</span>
							</Command.Item>
						{/each}
					</Command.Group>
				{/if}
			</Command.List>
		</Command.Root>
	</Popover.Content>
</Popover.Root>
