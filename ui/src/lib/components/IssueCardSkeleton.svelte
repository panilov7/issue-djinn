<script lang="ts">
	import { Skeleton } from '$lib/components/ui/skeleton';
</script>

<div class="flex items-center gap-4 px-4 py-3" aria-hidden="true">
	<Skeleton class="h-4 w-10" />
	<Skeleton class="h-4 flex-1" />
	<Skeleton class="hidden h-4 w-24 sm:block" />
	<Skeleton class="size-5 rounded-full" />
</div>
