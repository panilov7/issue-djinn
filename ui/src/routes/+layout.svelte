<script lang="ts">
	import '../app.css';
	import { onMount } from 'svelte';
	import { ModeWatcher } from 'mode-watcher';
	import ListIcon from '@lucide/svelte/icons/list';
	import SettingsIcon from '@lucide/svelte/icons/settings';
	import InfoIcon from '@lucide/svelte/icons/info';
	import { page } from '$app/stores';
	import { resolve } from '$app/paths';
	import { Button } from '$lib/components/ui/button';
	import { Toaster } from '$lib/components/ui/sonner';
	import ThemeToggle from '$lib/components/ThemeToggle.svelte';
	import UsernameInlineEdit from '$lib/components/UsernameInlineEdit.svelte';
	import { issueEvents } from '$lib/stores/issueEvents.svelte';

	let { children } = $props();

	// The tab's one SSE connection lives here, not per page: pages subscribe to
	// the shared store. Production binds the native EventSource; tests bind a
	// fake through the same factory seam.
	onMount(() => issueEvents.connect((url) => new EventSource(url)));
</script>

<ModeWatcher />

<div class="min-h-screen bg-background text-foreground">
	<header class="border-b">
		<div class="mx-auto max-w-7xl px-6 py-4 md:px-10 lg:px-16 flex items-center gap-4">
			<a href={resolve('/')} class="font-mono text-sm font-semibold tracking-tight">issue-djinn</a>
			<nav class="flex items-center gap-1 ml-2">
				<Button
					variant={$page.url.pathname === '/' ? 'secondary' : 'ghost'}
					size="sm"
					href="/"
				>
					<ListIcon data-icon="inline-start" />
					Issues
				</Button>
				<Button
					variant={$page.url.pathname.startsWith('/settings') ? 'secondary' : 'ghost'}
					size="sm"
					href="/settings"
				>
					<SettingsIcon data-icon="inline-start" />
					Settings
				</Button>
				<Button
					variant={$page.url.pathname.startsWith('/about') ? 'secondary' : 'ghost'}
					size="sm"
					href="/about"
				>
					<InfoIcon data-icon="inline-start" />
					About
				</Button>
			</nav>
			<div class="ml-auto flex items-center gap-2">
				<UsernameInlineEdit compact />
				<ThemeToggle compact />
			</div>
		</div>
	</header>

	<main class="mx-auto max-w-7xl px-6 py-10 md:px-10 lg:px-16 md:py-14">
		{@render children()}
	</main>
</div>

<Toaster />
