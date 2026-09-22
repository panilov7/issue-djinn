<script lang="ts">
	import { Badge } from '$lib/components/ui/badge';
	import type { IssueStatus } from '$lib/types';

	let { status, title }: { status: IssueStatus; title?: string } = $props();
</script>

<Badge
	class={status === 'open' ? 'bg-warning text-warning-foreground' : 'bg-closed text-closed-foreground'}
	{title}
>
	{status}
</Badge>