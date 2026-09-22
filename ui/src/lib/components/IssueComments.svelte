<script lang="ts">
	import * as Alert from '$lib/components/ui/alert';
	import { Button } from '$lib/components/ui/button';
	import * as Card from '$lib/components/ui/card';
	import * as Field from '$lib/components/ui/field';
	import { Input } from '$lib/components/ui/input';
	import MarkdownEditor from './MarkdownEditor.svelte';
	import InitialsAvatar from './InitialsAvatar.svelte';
	import { apiErrorMessage } from '$lib/api/client';
	import { EditingGate } from '$lib/stores/editingGate.svelte';
	import { username } from '$lib/stores/username.svelte';
	import type { Comment } from '$lib/types';
	import { relativeTime } from '$lib/utils/time';
	import MarkdownView from './MarkdownView.svelte';

	let {
		comments = [],
		onAdd,
		gate
	}: {
		comments?: Comment[];
		onAdd: (author: string, body: string) => Promise<void>;
		/** The page's editing gate; an open composer holds unsaved work. */
		gate?: EditingGate;
	} = $props();

	let adding = $state(false);
	let author = $state('');
	let body = $state('');
	let submitting = $state(false);
	let error = $state<string | null>(null);

	const canSubmit = $derived(author.trim().length > 0 && body.trim().length > 0 && !submitting);

	// The composer holds unsaved work while it is open, empty or not: the page
	// defers its event-driven refreshes until it closes. The gate
	// is the page's one instance, stable for this component's lifetime.
	// svelte-ignore state_referenced_locally
	gate?.holdWhile('comment-composer', () => adding);

	function openForm() {
		author = username.current ?? '';
		body = '';
		error = null;
		adding = true;
	}

	function cancel() {
		adding = false;
		error = null;
	}

	async function submit() {
		if (!canSubmit) return;
		submitting = true;
		error = null;
		try {
			await onAdd(author, body);
			adding = false;
			body = '';
		} catch (e) {
			error = apiErrorMessage(e);
		} finally {
			submitting = false;
		}
	}
</script>

<Card.Root>
	<Card.Header>
		<Card.Title>Comments <span class="text-muted-foreground">({comments.length})</span></Card.Title>
	</Card.Header>
	<Card.Content>
		{#if comments.length === 0}
			<p class="text-sm text-muted-foreground">No comments yet.</p>
		{:else}
			<!-- Linear-style flat feed: hairline dividers between items, no per-comment cards. -->
			<ul class="flex flex-col divide-y divide-border">
				{#each comments as comment (comment.id)}
					<li class="flex flex-col gap-1 py-3 first:pt-0 last:pb-0">
						<div class="flex items-center gap-2 text-sm text-muted-foreground">
							<InitialsAvatar name={comment.author} />
							<span class="font-medium text-foreground">{comment.author}</span>
							· {relativeTime(comment.createdAt)}
						</div>
						<MarkdownView content={comment.body} />
					</li>
				{/each}
			</ul>
		{/if}
	</Card.Content>
	<!-- Composer zone: `block` overrides the footer primitive's flex so the form
		spans full width; `border-border` pins the divider to the theme token
		(Tailwind v4 defaults `border-t` color to currentColor). -->
	<Card.Footer class="block border-border">
		{#if adding}
			<form
				class="flex flex-col gap-3"
				onsubmit={(e) => {
					e.preventDefault();
					void submit();
				}}
			>
				<Field.Field>
					<Field.FieldLabel for="comment-author">Author</Field.FieldLabel>
					<Input id="comment-author" bind:value={author} autocomplete="off" />
				</Field.Field>
				<Field.Field>
					<Field.FieldLabel for="comment-body">Comment</Field.FieldLabel>
					<MarkdownEditor
						id="comment-body"
						bind:value={body}
						placeholder="Write a comment..."
					/>
				</Field.Field>
				{#if error}
					<Alert.Root variant="destructive" role="alert">
						<Alert.Title>Could not add comment</Alert.Title>
						<Alert.Description>{error}</Alert.Description>
					</Alert.Root>
				{/if}
				<div class="flex gap-2">
					<Button type="submit" disabled={!canSubmit}>Add comment</Button>
					<Button type="button" variant="ghost" onclick={cancel}>Cancel</Button>
				</div>
			</form>
		{:else}
			<div class="flex justify-center">
				<Button variant="outline" size="sm" onclick={openForm}>Add comment</Button>
			</div>
		{/if}
	</Card.Footer>
</Card.Root>
