<script lang="ts">
	import TagsIcon from '@lucide/svelte/icons/tags';
	import { buttonVariants } from '$lib/components/ui/button';
	import { Checkbox } from '$lib/components/ui/checkbox';
	import * as Popover from '$lib/components/ui/popover';

	let {
		labels = [],
		value = [],
		onchange
	}: {
		labels: string[];
		value: string[];
		onchange?: (value: string[]) => void;
	} = $props();

	function toggle(label: string) {
		const next = value.includes(label)
			? value.filter((l) => l !== label)
			: [...value, label];
		onchange?.(next);
	}
</script>

<Popover.Root>
	<Popover.Trigger class={buttonVariants({ variant: 'outline', size: 'sm' })}>
		<TagsIcon data-icon="inline-start" />
		Labels
		{#if value.length > 0}
			<span class="text-muted-foreground">({value.length})</span>
		{/if}
	</Popover.Trigger>
	<Popover.Content align="start" class="w-60 p-1">
		<div class="flex flex-col gap-0.5">
			{#each labels as label (label)}
				<label
					class="flex cursor-pointer items-center gap-2 rounded-md px-2 py-1.5 hover:bg-muted"
				>
					<Checkbox checked={value.includes(label)} onCheckedChange={() => toggle(label)} />
					<span class="truncate text-sm">{label}</span>
				</label>
			{/each}
			{#if labels.length === 0}
				<p class="px-2 py-1.5 text-sm text-muted-foreground">No labels yet.</p>
			{/if}
		</div>
	</Popover.Content>
</Popover.Root>
