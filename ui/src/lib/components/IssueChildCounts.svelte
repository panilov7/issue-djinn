<script lang="ts">
	import type { ChildCounts } from '$lib/types';

	/** The first-level child counts of the row this line belongs to. */
	let { counts }: { counts: ChildCounts } = $props();
</script>

<span class="block truncate text-sm text-muted-foreground">
	{counts.open} open · {counts.closed} closed · {counts.total} total
</span>