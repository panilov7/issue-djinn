<script lang="ts">
	import { invalidate } from '$app/navigation';
	import { toast } from 'svelte-sonner';
	import * as Alert from '$lib/components/ui/alert';
	import { addComment } from '$lib/api/comments';
	import { addDependency, removeDependency } from '$lib/api/dependencies';
	import { closeIssue, reopenIssue, unassignIssue, updateIssue } from '$lib/api/issues';
	import { apiErrorMessage } from '$lib/api/client';
	import { issueEvents } from '$lib/stores/issueEvents.svelte';
	import { EditingGate } from '$lib/stores/editingGate.svelte';
	import { username } from '$lib/stores/username.svelte';
	import { storeChildDirection } from '$lib/stores/childDirection.svelte';
	import type { SortDirection } from '$lib/types';
	import CloseReopenDialog from '$lib/components/CloseReopenDialog.svelte';
	import type { IssueFormValues } from '$lib/components/IssueForm.svelte';
	import IssueChildren from '$lib/components/IssueChildren.svelte';
	import IssueComments from '$lib/components/IssueComments.svelte';
	import IssueDependencies from '$lib/components/IssueDependencies.svelte';
	import IssueDescription from '$lib/components/IssueDescription.svelte';
	import IssueEditForm from '$lib/components/IssueEditForm.svelte';
	import IssueHeader from '$lib/components/IssueHeader.svelte';

	let { data } = $props();

	// Component `$state` owns UI state (edit mode, dialog); server data comes
	// from the load function and refreshes via `invalidate`.
	let editing = $state(false);
	let transitionOpen = $state(false);
	// Set by a failed dependency add — a cycle attempt lands here — and cleared by
	// the next successful add. Tagged with the issue it came from, because SvelteKit
	// reuses this component across client-side navigations between detail pages.
	let failedDependencyAdd = $state<{ issueId: number; message: string } | null>(null);

	// The editing gate: the page's forms holding unsaved work
	// register here — the page-owned ones below, the comment composer and the
	// child-issue dialog via the gate prop. While anything holds, event-driven
	// refreshes defer into `pendingRefresh` and the indicator shows; they run
	// when the last surface closes.
	const editingGate = new EditingGate();
	let pendingRefresh = $state(false);

	// The children card's direction choice is this page's own memento,
	// separate from the list page's filter snapshot. A flip persists
	// immediately and refetches through the same invalidate path the event
	// store drives — so the editing gate defers it while a form holds unsaved
	// work, exactly like a live-update refresh. The toggle shows the direction
	// the load served (`data.childDirection`), so it and the list below it
	// always agree on what was fetched.
	function handleChildDirectionChange(direction: SortDirection) {
		storeChildDirection(direction);
		requestDetailRefresh();
	}

	const cycleError = $derived(
		failedDependencyAdd?.issueId === data.issue.id ? failedDependencyAdd.message : null
	);

	// Live refresh: the SSE store notifies this page once per event.
	// The same invalidation the page's own mutations use re-runs the load, and
	// only when the event names the issue on screen — as the mutated issue or
	// as a counterpart (dependency edit, reparent). Each (re)connect of the
	// stream refreshes once too, so events missed during a disconnection gap
	// self-heal without server-side replay. Both paths defer while the editing
	// gate holds unsaved work so an agent's change never lands
	// under an open form; the deferred refresh runs on the last close.
	function requestDetailRefresh() {
		if (editingGate.holdingUnsavedWork) {
			pendingRefresh = true;
		} else {
			void invalidate('app:issue-detail');
		}
	}

	$effect(
		() =>
			issueEvents.onEvent((event) => {
				if (event.issueIds.includes(data.issue.id)) requestDetailRefresh();
			})
	);
	$effect(() => issueEvents.onOpen(() => requestDetailRefresh()));

	// The deferred refresh runs exactly once, whatever number of events were
	// deferred behind the gate.
	$effect(() => {
		if (editingGate.holdingUnsavedWork || !pendingRefresh) return;
		pendingRefresh = false;
		void invalidate('app:issue-detail');
	});

	// A pending refresh belongs to the issue it was deferred on. SvelteKit
	// reuses this component across navigations between detail pages, so a
	// navigation discards it — the new issue's own load brought fresh data.
	$effect(() => {
		void data.issue.id;
		pendingRefresh = false;
	});

	editingGate.holdWhile('edit-form', () => editing);
	editingGate.holdWhile('close-reopen-dialog', () => transitionOpen);

	async function handleSave(values: IssueFormValues) {
		// `labels` is a full-array replacement, so an emptied list removes them all.
		await updateIssue(data.issue.id, {
			title: values.title,
			description: values.description,
			labels: values.labels
		});
		editing = false;
		await invalidate('app:issue-detail');
		toast.success('Issue updated');
	}

	async function handleClose(comment: string, author: string) {
		await closeIssue(data.issue.id, comment, author);
		await invalidate('app:issue-detail');
		toast.success('Issue closed');
	}

	async function handleReopen(comment: string, author: string) {
		await reopenIssue(data.issue.id, comment, author);
		await invalidate('app:issue-detail');
		toast.success('Issue reopened');
	}

	async function handleClaim() {
		const name = username.current;
		if (!name) {
			toast.error('Set a username first');
			return;
		}
		await updateIssue(data.issue.id, { assignee: name });
		await invalidate('app:issue-detail');
		toast.success('Issue claimed');
	}

	async function handleUnclaim() {
		await unassignIssue(data.issue.id);
		await invalidate('app:issue-detail');
		toast.success('Issue unclaimed');
	}

	async function handleAddComment(author: string, body: string) {
		await addComment(data.issue.id, author, body);
		await invalidate('app:issue-detail');
		toast.success('Comment added');
	}

	async function handleAddDependency(dependencyId: number) {
		try {
			await addDependency(data.issue.id, dependencyId);
			failedDependencyAdd = null;
			await invalidate('app:issue-detail');
			toast.success('Dependency added');
		} catch (error) {
			failedDependencyAdd = { issueId: data.issue.id, message: apiErrorMessage(error) };
		}
	}

	async function handleRemoveDependency(dependencyId: number) {
		try {
			await removeDependency(data.issue.id, dependencyId);
			await invalidate('app:issue-detail');
			toast.success('Dependency removed');
		} catch (error) {
			toast.error(apiErrorMessage(error));
		}
	}

	async function handleChildCreated() {
		await invalidate('app:issue-detail');
	}
</script>

<div class="flex flex-col gap-6">
	{#if pendingRefresh}
		<Alert.Root role="status">
			<Alert.Title>Changes available</Alert.Title>
			<Alert.Description>
				This issue changed while a form was open. The page refreshes when you close it.
			</Alert.Description>
		</Alert.Root>
	{/if}
	{#if editing}
		<IssueEditForm
			title={data.issue.title}
			description={data.issue.description}
			labels={data.issue.labels}
			onSave={handleSave}
			onCancel={() => (editing = false)}
		/>
	{:else}
		<div class="flex flex-col gap-6">
			<IssueHeader
				issue={data.issue}
				onEdit={() => (editing = true)}
				onClose={() => (transitionOpen = true)}
				onReopen={() => (transitionOpen = true)}
				onClaim={handleClaim}
				onUnclaim={handleUnclaim}
			/>
			<IssueDescription description={data.issue.description} />
			<IssueDependencies
				issueId={data.issue.id}
				dependencies={data.issue.dependencies}
				dependents={data.issue.dependents}
				cycleError={cycleError}
				onAddDependency={handleAddDependency}
				onRemoveDependency={handleRemoveDependency}
			/>
			<IssueChildren
				issue={data.issue}
				onChildCreated={handleChildCreated}
				gate={editingGate}
				childDirection={data.childDirection}
				onchilddirectionchange={handleChildDirectionChange}
			/>
			<IssueComments comments={data.comments} onAdd={handleAddComment} gate={editingGate} />
		</div>
	{/if}
</div>

<CloseReopenDialog
	bind:open={transitionOpen}
	status={data.issue.status}
	onConfirm={data.issue.status === 'open' ? handleClose : handleReopen}
/>
