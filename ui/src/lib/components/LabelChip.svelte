<script lang="ts">
	import { Badge } from '$lib/components/ui/badge';

	let { label }: { label: string } = $props();
</script>

<Badge variant="outline" class="max-w-40 truncate">{label}</Badge>
