<script lang="ts">
	import * as Card from '$lib/components/ui/card';
	import * as Field from '$lib/components/ui/field';
	import ThemeToggle from '$lib/components/ThemeToggle.svelte';
	import UsernameInlineEdit from '$lib/components/UsernameInlineEdit.svelte';
	import McpServerSection from '$lib/components/McpServerSection.svelte';
</script>

<div class="flex flex-col gap-8 md:gap-10">
	<h1 class="text-2xl font-semibold tracking-tight">Settings</h1>

	<Card.Root>
		<Card.Header>
			<Card.Title>Username</Card.Title>
			<Card.Description>
				The name used when you comment, claim, or close issues.
			</Card.Description>
		</Card.Header>
		<Card.Content>
			<Field.Field orientation="horizontal" class="items-center gap-4">
				<Field.FieldLabel for="settings-username" class="sr-only">Username</Field.FieldLabel>
				<UsernameInlineEdit />
			</Field.Field>
		</Card.Content>
	</Card.Root>

	<Card.Root>
		<Card.Header>
			<Card.Title>Theme</Card.Title>
			<Card.Description>Choose light, dark, or follow the system preference.</Card.Description>
		</Card.Header>
		<Card.Content>
			<ThemeToggle />
		</Card.Content>
	</Card.Root>

	<McpServerSection />
</div>
