<script lang="ts">
	import { previewHtml } from '$lib/utils/markdown-editor';

	let { content = '' }: { content?: string } = $props();
</script>

<div
	class="prose prose-sm max-w-none dark:prose-invert"
	data-slot="markdown-view"
	data-testid="markdown-view"
>
	<!-- Content is DOMPurify-sanitized by `previewHtml`; server never renders markdown. -->
	<!-- eslint-disable-next-line svelte/no-at-html-tags -->
	{@html previewHtml(content)}
</div>
