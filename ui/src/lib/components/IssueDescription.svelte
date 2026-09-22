<script lang="ts">
	import * as Card from '$lib/components/ui/card';
	import MarkdownView from './MarkdownView.svelte';

	let { description = '' }: { description?: string } = $props();
</script>

<!-- Same card anatomy as the Children card, minus the action slot: the Edit
     button stays in the issue header, not inside this card. -->
<Card.Root>
	<Card.Header>
		<Card.Title>Description</Card.Title>
	</Card.Header>
	<Card.Content>
		<MarkdownView content={description} />
	</Card.Content>
</Card.Root>
