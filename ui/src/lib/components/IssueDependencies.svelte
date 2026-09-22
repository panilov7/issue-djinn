<script lang="ts">
	import PlusIcon from '@lucide/svelte/icons/plus';
	import XIcon from '@lucide/svelte/icons/x';
	import * as Alert from '$lib/components/ui/alert';
	import { Button, buttonVariants } from '$lib/components/ui/button';
	import * as Card from '$lib/components/ui/card';
	import type { IssueDependency } from '$lib/types';
	import IssuePicker from './IssuePicker.svelte';
	import StatusBadge from './StatusBadge.svelte';

	let {
		issueId,
		dependencies = [],
		dependents = [],
		cycleError = null,
		onAddDependency,
		onRemoveDependency
	}: {
		/** This issue's id — the picker must never offer an issue as its own dependency. */
		issueId: number;
		dependencies?: IssueDependency[];
		dependents?: IssueDependency[];
		/** Message from the last failed add — a cycle attempt lands here, verbatim from the API. */
		cycleError?: string | null;
		onAddDependency?: (dependencyId: number) => Promise<void> | void;
		onRemoveDependency?: (dependencyId: number) => Promise<void> | void;
	} = $props();

	// The picker offers every status, minus this issue and everything this issue
	// already depends on — a closed dependency is a legal one.
	const excludeIds = $derived([issueId, ...dependencies.map((dep) => dep.id)]);
</script>

{#snippet dependencyList(items: IssueDependency[], removable: boolean)}
	{#if items.length === 0}
		<p class="text-sm text-muted-foreground">No dependencies.</p>
	{:else}
		<ul class="flex flex-col gap-1.5">
			{#each items as dep (dep.id)}
				<li class="flex items-center gap-1">
					<a
						class="flex min-w-0 flex-1 items-center gap-2 text-sm hover:underline"
						href={`/issues/${dep.id}`}
					>
						<span class="font-mono text-muted-foreground">#{dep.id}</span>
						<span class="truncate">{dep.title}</span>
						<StatusBadge status={dep.status} />
					</a>
					{#if removable}
						<Button
							variant="ghost"
							size="icon-xs"
							aria-label={`Remove dependency ${dep.title}`}
							onclick={() => onRemoveDependency?.(dep.id)}
						>
							<XIcon />
						</Button>
					{/if}
				</li>
			{/each}
		</ul>
	{/if}
{/snippet}

<div class="grid gap-4 md:grid-cols-2" data-slot="issue-dependencies">
	<Card.Root>
		<Card.Header>
			<Card.Title>Depends on</Card.Title>
			<Card.Action>
				<IssuePicker
					status="all"
					excludeIds={excludeIds}
					onSelect={(issue) => onAddDependency?.(issue.id)}
					aria-label="Add dependency"
					class={buttonVariants({ variant: 'outline', size: 'sm' })}
				>
					<PlusIcon data-icon="inline-start" />
					Add dependency
				</IssuePicker>
			</Card.Action>
		</Card.Header>
		<Card.Content>
			<div class="flex flex-col gap-3">
				{#if cycleError}
					<Alert.Root variant="destructive" role="alert">
						<Alert.Title>Could not add dependency</Alert.Title>
						<Alert.Description>{cycleError}</Alert.Description>
					</Alert.Root>
				{/if}
				{@render dependencyList(dependencies, true)}
			</div>
		</Card.Content>
	</Card.Root>
	<Card.Root>
		<Card.Header>
			<Card.Title>Dependents</Card.Title>
		</Card.Header>
		<Card.Content>{@render dependencyList(dependents, false)}</Card.Content>
	</Card.Root>
</div>
