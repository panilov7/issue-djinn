<script lang="ts">
	import { toast } from 'svelte-sonner';
	import CopyIcon from '@lucide/svelte/icons/copy';
	import CheckIcon from '@lucide/svelte/icons/check';
	import { Button } from '$lib/components/ui/button';
	import { copyToClipboard } from '$lib/utils/clipboard';

	let {
		value,
		label,
		variant = 'outline',
		size = 'sm'
	}: {
		/** The text copied to the clipboard on click. */
		value: string;
		/** Visible + accessible button text describing what is copied. */
		label: string;
		variant?: 'default' | 'outline' | 'secondary' | 'ghost' | 'destructive' | 'link';
		size?: 'default' | 'xs' | 'sm' | 'lg' | 'icon' | 'icon-xs' | 'icon-sm' | 'icon-lg';
	} = $props();

	let copied = $state(false);

	async function onCopy() {
		const ok = await copyToClipboard(value);
		if (!ok) {
			toast.error('Copy failed — please copy manually');
			return;
		}
		toast.success('Copied to clipboard');
		copied = true;
		window.setTimeout(() => {
			copied = false;
		}, 1600);
	}
</script>

<Button {variant} {size} onclick={onCopy} aria-label={`Copy ${label}`}>
	{#if copied}
		<CheckIcon data-icon="inline-start" />
	{:else}
		<CopyIcon data-icon="inline-start" />
	{/if}
	{label}
</Button>
