<script lang="ts">
	import * as Avatar from '$lib/components/ui/avatar';

	let { name }: { name: string | null } = $props();

	function initials(name: string): string {
		const parts = name.trim().split(/\s+/).filter(Boolean);
		const first = parts[0]?.[0] ?? '?';
		const last = parts.length > 1 ? parts[parts.length - 1][0] : '';
		return (first + last).toUpperCase();
	}
</script>

{#if name}
	<Avatar.Root class="size-5" title={name}>
		<Avatar.Fallback class="text-[10px] font-medium">{initials(name)}</Avatar.Fallback>
	</Avatar.Root>
{/if}
