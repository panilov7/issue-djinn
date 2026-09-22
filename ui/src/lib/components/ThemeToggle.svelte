<script lang="ts">
	import SunIcon from '@lucide/svelte/icons/sun';
	import MoonIcon from '@lucide/svelte/icons/moon';
	import MonitorIcon from '@lucide/svelte/icons/monitor';
	import { resetMode, setMode, userPrefersMode } from 'mode-watcher';
	import { Button } from '$lib/components/ui/button';
	import * as DropdownMenu from '$lib/components/ui/dropdown-menu';

	let { compact = false }: { compact?: boolean } = $props();

	const current = $derived(userPrefersMode.current);
</script>

<DropdownMenu.Root>
	<DropdownMenu.Trigger>
		<Button variant="ghost" size={compact ? 'icon' : 'default'} aria-label="Toggle theme">
			{#if compact}
				<SunIcon class="dark:hidden" data-icon="inline-start" />
				<MoonIcon class="hidden dark:block" data-icon="inline-start" />
			{:else}
				Theme
			{/if}
		</Button>
	</DropdownMenu.Trigger>
	<DropdownMenu.Content align="end">
		<DropdownMenu.Group>
			<DropdownMenu.Item onclick={() => setMode('light')}>
				<SunIcon data-icon="inline-start" />
				Light
				{#if current === 'light'}<span class="ml-auto text-muted-foreground">·</span>{/if}
			</DropdownMenu.Item>
			<DropdownMenu.Item onclick={() => setMode('dark')}>
				<MoonIcon data-icon="inline-start" />
				Dark
				{#if current === 'dark'}<span class="ml-auto text-muted-foreground">·</span>{/if}
			</DropdownMenu.Item>
			<DropdownMenu.Item onclick={() => resetMode()}>
				<MonitorIcon data-icon="inline-start" />
				System
				{#if current === 'system'}<span class="ml-auto text-muted-foreground">·</span>{/if}
			</DropdownMenu.Item>
		</DropdownMenu.Group>
	</DropdownMenu.Content>
</DropdownMenu.Root>
