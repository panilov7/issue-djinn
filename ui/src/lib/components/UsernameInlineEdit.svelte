<script lang="ts">
	import CheckIcon from '@lucide/svelte/icons/check';
	import PencilIcon from '@lucide/svelte/icons/pencil';
	import XIcon from '@lucide/svelte/icons/x';
	import { toast } from 'svelte-sonner';
	import { Button } from '$lib/components/ui/button';
	import * as InputGroup from '$lib/components/ui/input-group';
	import { username } from '$lib/stores/username.svelte';

	let { compact = false }: { compact?: boolean } = $props();

	let editing = $state(false);
	let draft = $state('');
	let container: HTMLDivElement | null = $state(null);

	function startEdit() {
		draft = username.current ?? '';
		editing = true;
	}

	function save() {
		username.set(draft);
		editing = false;
		toast.success('Username saved');
	}

	function cancel() {
		editing = false;
	}

	function onKeydown(e: KeyboardEvent) {
		if (e.key === 'Enter') {
			e.preventDefault();
			save();
		} else if (e.key === 'Escape') {
			e.preventDefault();
			cancel();
		}
	}

	$effect(() => {
		if (editing) {
			queueMicrotask(() => {
				const input = container?.querySelector('input');
				input?.focus();
				const len = input?.value.length ?? 0;
				input?.setSelectionRange(len, len);
			});
		}
	});

	function clickOutside(node: HTMLElement) {
		function handle(e: MouseEvent) {
			if (!node.contains(e.target as Node)) cancel();
		}
		document.addEventListener('click', handle, true);
		return {
			destroy() {
				document.removeEventListener('click', handle, true);
			}
		};
	}
</script>

{#if editing}
	<div
		bind:this={container}
		use:clickOutside
		onkeydown={(e) => {
			if (e.key === 'Escape') {
				e.preventDefault();
				cancel();
			}
		}}
		role="presentation"
	>
		<InputGroup.Root class="w-48">
			<InputGroup.Input
				bind:value={draft}
				placeholder="Your name"
				onkeydown={onKeydown}
				aria-label="Username"
				autocomplete="off"
			/>
			<InputGroup.Addon align="inline-end">
				<InputGroup.Button size="icon-xs" variant="ghost" onclick={save} aria-label="Save username">
					<CheckIcon data-icon="inline-start" />
				</InputGroup.Button>
				<InputGroup.Button size="icon-xs" variant="ghost" onclick={cancel} aria-label="Cancel">
					<XIcon data-icon="inline-start" />
				</InputGroup.Button>
			</InputGroup.Addon>
		</InputGroup.Root>
	</div>
{:else if username.current}
	<Button variant="ghost" size={compact ? 'sm' : 'default'} onclick={startEdit}>
		{username.current}
	</Button>
{:else}
	<Button variant="ghost" size={compact ? 'sm' : 'default'} onclick={startEdit}>
		<PencilIcon data-icon="inline-start" />
		Set username
	</Button>
{/if}
