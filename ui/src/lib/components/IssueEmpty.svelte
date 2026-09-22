<script lang="ts">
	import * as Empty from '$lib/components/ui/empty';
	import InboxIcon from '@lucide/svelte/icons/inbox';
	import type { WorkFilter } from '$lib/types';

	let { workFilter }: { workFilter?: WorkFilter } = $props();

	// The empty state knows which work-state view it sits in: a frontier
	// emptiness means nothing is claimable, an In progress emptiness means
	// nothing is claimed — different diagnoses, different next moves.
</script>

<Empty.Root class="py-12">
	<Empty.Content>
		<InboxIcon class="size-6 text-muted-foreground" />
		<Empty.Title>No issues found</Empty.Title>
		<Empty.Description>
			{#if workFilter === 'frontier'}
				Nothing on the frontier right now — no open issue is unclaimed and unblocked. Try
				deselecting the work-state filter, or create one.
			{:else if workFilter === 'inProgress'}
				Nothing in progress right now — no open issue is claimed and unblocked. Try deselecting
				the work-state filter, or claim something from the frontier.
			{:else}
				No issues match these filters.
			{/if}
		</Empty.Description>
	</Empty.Content>
</Empty.Root>
