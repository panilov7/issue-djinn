<script lang="ts" module>
	/** The field values a completed form hands back to its shell. */
	export interface IssueFormValues {
		title: string;
		description: string;
		labels: string[];
		/** The picked parent, or null. Always null in edit mode — an edit never moves an issue. */
		parentId: number | null;
	}
</script>

<script lang="ts">
	import * as Alert from '$lib/components/ui/alert';
	import { Button, buttonVariants } from '$lib/components/ui/button';
	import * as Field from '$lib/components/ui/field';
	import { Input } from '$lib/components/ui/input';
	import { cn } from '$lib/utils';
	import { apiErrorMessage } from '$lib/api/client';
	import ChevronsUpDownIcon from '@lucide/svelte/icons/chevrons-up-down';
	import type { IssueSummary } from '$lib/types';
	import IssuePicker from './IssuePicker.svelte';
	import LabelTagInput from './LabelTagInput.svelte';
	import MarkdownEditor from './MarkdownEditor.svelte';

	let {
		mode = 'create',
		initialTitle = '',
		initialDescription = '',
		initialLabels = [],
		initialParent = null,
		onSubmit,
		onCancel,
		class: className
	}: {
		/** `create` renders the parent picker; `edit` seeds the fields from an existing issue. */
		mode?: 'create' | 'edit';
		/** Draft seeds — read once, so fresh props never clobber a draft in progress. */
		initialTitle?: string;
		initialDescription?: string;
		initialLabels?: string[];
		/** The parent the picker starts from, e.g. the "Add child issue" flow's viewed issue. */
		initialParent?: IssueSummary | null;
		/** Called with the field values; a rejection is surfaced as the form's error alert. */
		onSubmit: (values: IssueFormValues) => void | Promise<void>;
		/** Called when the user dismisses the form. */
		onCancel: () => void;
		/** Extra classes for the form root, e.g. a dialog shell's scroll classes. */
		class?: string;
	} = $props();

	// svelte-ignore state_referenced_locally
	let title = $state(initialTitle);
	// svelte-ignore state_referenced_locally
	let description = $state(initialDescription);
	// svelte-ignore state_referenced_locally
	let labels = $state([...initialLabels]);
	// svelte-ignore state_referenced_locally
	let parent = $state<IssueSummary | null>(initialParent);
	let titleTouched = $state(false);
	let submitting = $state(false);
	let submitError = $state<string | null>(null);

	const titleEmpty = $derived(title.trim().length === 0);
	// Wait until the user has actually interacted before announcing an error.
	const titleError = $derived(titleEmpty && titleTouched);
	const canSave = $derived(!titleEmpty && !submitting);
	const submitLabel = $derived(mode === 'create' ? 'Create issue' : 'Save changes');

	async function submit() {
		titleTouched = true;
		if (!canSave) return;
		submitting = true;
		submitError = null;
		try {
			await onSubmit({
				title: title.trim(),
				description,
				labels,
				parentId: parent?.id ?? null
			});
		} catch (e) {
			submitError = apiErrorMessage(e);
		} finally {
			submitting = false;
		}
	}

	function clearParent() {
		parent = null;
	}
</script>

<form
	class={cn('flex flex-col gap-4', className)}
	data-slot="issue-form"
	onsubmit={(e) => {
		e.preventDefault();
		void submit();
	}}
>
	<Field.FieldGroup class="gap-4">
		<Field.Field data-invalid={titleError}>
			<Field.FieldLabel for="issue-form-title">Title</Field.FieldLabel>
			<Input
				id="issue-form-title"
				bind:value={title}
				placeholder="A short, clear title"
				aria-invalid={titleError}
				onblur={() => (titleTouched = true)}
			/>
			{#if titleError}
				<Field.FieldError>Title is required.</Field.FieldError>
			{/if}
		</Field.Field>

		<!-- MarkdownEditor renders its own visible label for the textarea; a
		     second one here would duplicate the control's accessible name. -->
		<MarkdownEditor
			id="issue-form-description"
			label="Description"
			rows={6}
			bind:value={description}
		/>

		{#if mode === 'create'}
			<Field.Field>
				<Field.FieldLabel>Parent issue</Field.FieldLabel>
				<!-- Open-only: an issue's parent is the work it belongs under, and
				     closed work is not something to attach new work to. -->
				<IssuePicker
					status="open"
					class={cn(buttonVariants({ variant: 'outline' }), 'w-full justify-between')}
					aria-label={
						parent ? `Parent issue: #${parent.id} ${parent.title}` : 'Select parent issue'
					}
					onSelect={(issue) => (parent = issue)}
				>
					{#if parent}
						#{parent.id} {parent.title}
					{:else}
						No parent
					{/if}
					<ChevronsUpDownIcon class="size-4" />
				</IssuePicker>
				{#if parent}
					<Button
						type="button"
						variant="ghost"
						size="sm"
						class="self-start"
						onclick={clearParent}
					>
						Clear parent
					</Button>
				{/if}
			</Field.Field>
		{/if}

		<!-- LabelTagInput's input names itself via aria-label, so this label
		     has no `for` to point at. -->
		<Field.Field>
			<Field.FieldLabel>Labels</Field.FieldLabel>
			<LabelTagInput value={labels} onchange={(next) => (labels = next)} />
			<Field.FieldDescription>
				Free-form labels. Press Enter or comma to add; anything still typed is added on save.
			</Field.FieldDescription>
		</Field.Field>
	</Field.FieldGroup>

	{#if submitError}
		<Alert.Root variant="destructive" role="alert">
			<Alert.Title>Could not save issue</Alert.Title>
			<Alert.Description>{submitError}</Alert.Description>
		</Alert.Root>
	{/if}

	<div class="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
		<Button type="button" variant="ghost" onclick={onCancel}>Cancel</Button>
		<Button type="submit" disabled={!canSave}>{submitLabel}</Button>
	</div>
</form>
