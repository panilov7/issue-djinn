<script lang="ts">
	import CreateIssueDialog from '$lib/components/CreateIssueDialog.svelte';
	import IssueList from '$lib/components/IssueList.svelte';

	let { data } = $props();
</script>

<div class="flex flex-col gap-6">
	<div class="flex items-center justify-between gap-4">
		<h1 class="text-2xl font-semibold tracking-tight">Issues</h1>
		<CreateIssueDialog />
	</div>
	<IssueList labels={data.labels} />
</div>
