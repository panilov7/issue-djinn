<script lang="ts">
	import { goto } from '$app/navigation';
	import { toast } from 'svelte-sonner';
	import PlusIcon from '@lucide/svelte/icons/plus';
	import { createIssue } from '$lib/api/issues';
	import { buttonVariants } from '$lib/components/ui/button';
	import * as Dialog from '$lib/components/ui/dialog';
	import IssueForm, { type IssueFormValues } from './IssueForm.svelte';
	import { EditingGate } from '$lib/stores/editingGate.svelte';
	import type { IssueSummary } from '$lib/types';

	let {
		presetParent = null,
		triggerClass = buttonVariants(),
		onCreated,
		gate,
		children
	}: {
		/** Parent the form starts from — the "Add child issue" flow pre-selects the viewed issue. */
		presetParent?: IssueSummary | null;
		/** Classes for the trigger button, e.g. `buttonVariants({ variant: 'outline', size: 'sm' })`. */
		triggerClass?: string;
		/** Called after a successful create instead of navigating to the new issue. */
		onCreated?: () => Promise<void> | void;
		/** The page's editing gate; an open dialog holds unsaved work. */
		gate?: EditingGate;
		/** Trigger content; defaults to "+ New issue". */
		children?: import('svelte').Snippet;
	} = $props();

	let open = $state(false);

	// The open dialog holds unsaved work: the page defers its event-driven
	// refreshes until it closes. Pages without a gate (the issue
	// list) pass none. The gate, when passed, is the page's one instance.
	// svelte-ignore state_referenced_locally
	gate?.holdWhile('create-issue-dialog', () => open);

	async function handleCreate(values: IssueFormValues) {
		const created = await createIssue({
			title: values.title,
			description: values.description,
			parentId: values.parentId,
			labels: values.labels
		});
		// Close and confirm before navigating, so the toast is not clipped by
		// the page teardown.
		open = false;
		toast.success(`Issue #${created.id} created`);
		if (onCreated) {
			// The caller owns where the created issue shows up — the detail page
			// refreshes so a new child lands in its Children section.
			await onCreated();
		} else {
			await goto(`/issues/${created.id}`);
		}
	}
</script>

<Dialog.Root bind:open>
	<Dialog.Trigger class={triggerClass}>
		{#if children}
			{@render children()}
		{:else}
			<PlusIcon data-icon="inline-start" />
			New issue
		{/if}
	</Dialog.Trigger>
	<!-- One fluid cap: viewport-relative (the fixed content's 100% is the
		viewport), capped at 42rem — it grows smoothly on medium screens and
		replaces the primitive's stepped `sm:max-w-lg`. The primitive's untouched
		base rule still covers small screens (near-full-width, 1rem gutters). -->
	<!-- Height cap + vertical flex column: an oversized form scrolls in the
		body while the header (title, description, close button) stays fixed, so
		the dialog never grows past the viewport edge. `flex` replaces the
		primitive's `grid` default through the class merge. -->
	<Dialog.Content class="sm:max-w-[min(calc(100%-2rem),42rem)] flex max-h-[85vh] flex-col">
		<Dialog.Header>
			<Dialog.Title>New issue</Dialog.Title>
			<Dialog.Description>
				The title is the only required field. Parent and labels can also be set later.
			</Dialog.Description>
		</Dialog.Header>
		<!-- Keyed on open, so every open starts from a clean form; bits-ui owns mounting. -->
		{#key open}
			<IssueForm
				class="min-h-0 flex-1 overflow-y-auto"
				mode="create"
				initialParent={presetParent}
				onSubmit={handleCreate}
				onCancel={() => (open = false)}
			/>
		{/key}
	</Dialog.Content>
</Dialog.Root>
