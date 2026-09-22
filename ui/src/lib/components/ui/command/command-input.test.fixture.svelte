<script lang="ts">
	// Mounts command-input inside the Command.Root context it requires.
	// Used only by command-input.test.ts.
	import Root from './command.svelte';
	import Input from './command-input.svelte';
</script>

<Root>
	<Input />
</Root>