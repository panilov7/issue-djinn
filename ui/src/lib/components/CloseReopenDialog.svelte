<script lang="ts">
	import * as Alert from '$lib/components/ui/alert';
	import { Button, buttonVariants } from '$lib/components/ui/button';
	import * as Dialog from '$lib/components/ui/dialog';
	import * as Field from '$lib/components/ui/field';
	import { Input } from '$lib/components/ui/input';
	import MarkdownEditor from './MarkdownEditor.svelte';
	import { apiErrorMessage } from '$lib/api/client';
	import { username } from '$lib/stores/username.svelte';
	import type { IssueStatus } from '$lib/types';

	let {
		open = $bindable(false),
		status,
		onConfirm
	}: {
		open?: boolean;
		status: IssueStatus;
		onConfirm: (comment: string, author: string) => Promise<void>;
	} = $props();

	const closing = $derived(status === 'open');
	let comment = $state('');
	let author = $state('');
	let submitting = $state(false);
	let error = $state<string | null>(null);

	const canSubmit = $derived(
		comment.trim().length > 0 && author.trim().length > 0 && !submitting
	);

	$effect(() => {
		if (open) {
			comment = '';
			author = username.current ?? '';
			error = null;
		}
	});

	async function submit() {
		if (!canSubmit) return;
		submitting = true;
		error = null;
		try {
			await onConfirm(comment, author);
			open = false;
		} catch (e) {
			error = apiErrorMessage(e);
		} finally {
			submitting = false;
		}
	}
</script>

<Dialog.Root bind:open>
	<!-- One fluid cap at 36rem — wider than the old fixed 28rem cap, narrower
		than the create-issue dialog's 42rem. The primitive's untouched base rule
		still covers small screens (near-full-width, 1rem gutters). -->
	<!-- Height cap + vertical flex column: the form scrolls in the body while
		the header (title, description, close button) stays fixed. `flex`
		replaces the primitive's `grid` default through the class merge. -->
	<Dialog.Content class="sm:max-w-[min(calc(100%-2rem),36rem)] flex max-h-[85vh] flex-col">
		<Dialog.Header>
			<Dialog.Title>{closing ? 'Close issue' : 'Reopen issue'}</Dialog.Title>
			<Dialog.Description>
				{closing
					? 'Closing requires a resolution comment; it cannot be left blank.'
					: 'Reopening requires a comment explaining why.'}
			</Dialog.Description>
		</Dialog.Header>
		<form
			class="flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto"
			onsubmit={(e) => {
				e.preventDefault();
				void submit();
			}}
		>
			<Field.Field>
				<Field.FieldLabel for="transition-author">Author</Field.FieldLabel>
				<Input id="transition-author" bind:value={author} autocomplete="off" />
			</Field.Field>
			<Field.Field>
				<Field.FieldLabel for="transition-comment">Resolution comment</Field.FieldLabel>
				<MarkdownEditor
					id="transition-comment"
					bind:value={comment}
					placeholder={closing ? 'Describe how this was resolved...' : 'Explain why you are reopening...'}
				/>
			</Field.Field>
			{#if error}
				<Alert.Root variant="destructive" role="alert">
					<Alert.Title>Could not {closing ? 'close' : 'reopen'} issue</Alert.Title>
					<Alert.Description>{error}</Alert.Description>
				</Alert.Root>
			{/if}
			<Dialog.Footer>
				<Dialog.Close type="button" class={buttonVariants({ variant: 'ghost' })}>Cancel</Dialog.Close>
				<Button type="submit" disabled={!canSubmit}>
					{closing ? 'Close issue' : 'Reopen issue'}
				</Button>
			</Dialog.Footer>
		</form>
	</Dialog.Content>
</Dialog.Root>
