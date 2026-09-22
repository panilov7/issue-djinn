<script lang="ts">
	import * as Card from '$lib/components/ui/card';
	import CopyButton from '$lib/components/CopyButton.svelte';

	// Built from the current browser tab's origin — the UI and the MCP endpoint
	// are served by the same Quarkus server, so this always points at the live server.
	const mcpConfig = `{
  "mcpServers": {
    "issue-djinn": {
      "type": "http",
      "url": "${window.location.origin}/mcp"
    }
  }
}`;
</script>

<Card.Root>
	<Card.Header>
		<Card.Title>MCP Server</Card.Title>
		<Card.Description>
			Connect your AI assistant to issue-djinn via the Model Context Protocol.
		</Card.Description>
	</Card.Header>
	<Card.Content class="flex flex-col gap-4">
		<p class="text-sm leading-6 text-muted-foreground">
			Create a <code class="rounded bg-muted px-1 py-0.5 font-mono text-xs">.mcp.json</code> file
			in your project root with the following contents:
		</p>
		<pre
			class="overflow-x-auto rounded-md border border-border bg-muted p-3 font-mono text-xs leading-6"
		><code class="whitespace-pre">{mcpConfig}</code></pre>
		<div class="flex justify-end">
			<CopyButton value={mcpConfig} label=".mcp.json" />
		</div>
		<p class="text-sm leading-6 text-muted-foreground">
			This file is detected automatically by Claude Code and GitHub Copilot CLI; other MCP-aware
			tools that read <code class="rounded bg-muted px-1 py-0.5 font-mono text-xs">.mcp.json</code>
			should also work.
		</p>
	</Card.Content>
</Card.Root>
