<script lang="ts">
	import * as Card from '$lib/components/ui/card';
	import CopyButton from '$lib/components/CopyButton.svelte';

	// Mirrors scripts/run.sh: run the packaged fast-jar, overriding the default
	// data directory (~/.issue-djinn) via ISSUE_DJINN_DATA_DIR.
	const runCommand = 'ISSUE_DJINN_DATA_DIR=./data java -jar issue-djinn.jar';
</script>

<div class="flex flex-col gap-8 md:gap-10">
	<h1 class="text-2xl font-semibold tracking-tight">About</h1>

	<Card.Root>
		<Card.Header>
			<Card.Title>issue-djinn</Card.Title>
			<Card.Description>issue-djinn is a local issue tracker for a single user.</Card.Description>
		</Card.Header>
		<Card.Content class="flex flex-col gap-4">
			<dl class="flex flex-col gap-3 text-sm leading-6">
				<div>
					<dt class="font-semibold">Issues &amp; hierarchy</dt>
					<dd class="text-muted-foreground">
						Issues carry a title, description, status, and any number of opaque string
						markers. Large pieces of work decompose into parent/child trees, so an epic can
						be broken down as far as needed and each leaf tracked on its own.
					</dd>
				</div>
				<div>
					<dt class="font-semibold">Dependencies &amp; frontier</dt>
					<dd class="text-muted-foreground">
						Issues can depend on other issues, and a dependency blocks its dependent until
						it closes. The frontier view lists exactly the open issues with nothing left
						blocking them — the work that can start right now.
					</dd>
				</div>
				<div>
					<dt class="font-semibold">Assignment &amp; comments</dt>
					<dd class="text-muted-foreground">
						Issues can be claimed by a person or an agent, and every issue has a comment
						thread for design notes, progress updates, and review feedback.
					</dd>
				</div>
				<div>
					<dt class="font-semibold">Built for AI assistants</dt>
					<dd class="text-muted-foreground">
						A REST API and an MCP server (see below) expose the whole tracker, so AI coding
						assistants can create, claim, and close issues directly. Live updates stream to
						the UI over SSE, and everything is stored in a SQLite database under
						<code class="rounded bg-muted px-1 py-0.5 font-mono text-xs">~/.issue-djinn/</code
						>.
					</dd>
				</div>
			</dl>
			<div class="flex flex-col gap-2">
				<h3 class="text-sm font-semibold">Running from the packaged jar</h3>
				<pre
					class="overflow-x-auto rounded-md border border-border bg-muted p-3 font-mono text-xs leading-6"
				><code class="whitespace-pre">{runCommand}</code></pre>
				<div class="flex justify-end">
					<CopyButton value={runCommand} label="Run command" />
				</div>
				<p class="text-sm leading-6 text-muted-foreground">
					The <code class="rounded bg-muted px-1 py-0.5 font-mono text-xs"
						>ISSUE_DJINN_DATA_DIR</code
					> env. var overrides where the SQLite database lives; omit it to use the default
					<code class="rounded bg-muted px-1 py-0.5 font-mono text-xs">~/.issue-djinn/</code>.
				</p>
			</div>
			<p class="text-sm text-muted-foreground">
				Version <span class="font-mono">1.0.0</span>
			</p>
		</Card.Content>
	</Card.Root>
</div>
