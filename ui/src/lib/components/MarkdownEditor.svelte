<script lang="ts">
	import { Button, buttonVariants } from '$lib/components/ui/button';
	import { Separator } from '$lib/components/ui/separator';
	import * as Tooltip from '$lib/components/ui/tooltip';
	import MarkdownView from '$lib/components/MarkdownView.svelte';
	import {
		insertSyntaxAroundSelection,
		insertLinePrefix,
		prefixCurrentLine,
		insertFencedCodeBlock,
		insertHorizontalRule,
		insertCheckboxList,
		insertTable,
		handleTab,
		handleEnter,
		handleShortcut,
		type EditState
	} from '$lib/utils/markdown-editor';
	import { cn } from '$lib/utils';

	import BoldIcon from '@lucide/svelte/icons/bold';
	import ItalicIcon from '@lucide/svelte/icons/italic';
	import CodeIcon from '@lucide/svelte/icons/code';
	import LinkIcon from '@lucide/svelte/icons/link';
	import ListIcon from '@lucide/svelte/icons/list';
	import ListOrderedIcon from '@lucide/svelte/icons/list-ordered';
	import ListChecksIcon from '@lucide/svelte/icons/list-checks';
	import Heading1Icon from '@lucide/svelte/icons/heading-1';
	import Heading2Icon from '@lucide/svelte/icons/heading-2';
	import Heading3Icon from '@lucide/svelte/icons/heading-3';
	import QuoteIcon from '@lucide/svelte/icons/quote';
	import StrikethroughIcon from '@lucide/svelte/icons/strikethrough';
	import MinusIcon from '@lucide/svelte/icons/minus';
	import TableIcon from '@lucide/svelte/icons/table';
	import EyeIcon from '@lucide/svelte/icons/eye';
	import PencilIcon from '@lucide/svelte/icons/pencil';

	let {
		value = $bindable(''),
		id,
		label = 'Markdown',
		labelSrOnly = false,
		placeholder = 'Write in markdown…',
		rows = 6,
		class: className,
		previewClass = 'prose prose-sm dark:prose-invert max-w-none'
	}: {
		value?: string;
		id?: string;
		label?: string;
		labelSrOnly?: boolean;
		placeholder?: string;
		rows?: number;
		class?: string;
		previewClass?: string;
	} = $props();

	let textarea: HTMLTextAreaElement | null = $state(null);
	let mode = $state<'edit' | 'preview'>('edit');

	function toggleMode() {
		mode = mode === 'edit' ? 'preview' : 'edit';
	}

	function currentState(): EditState {
		const el = textarea;
		if (!el) return { value, selectionStart: value.length, selectionEnd: value.length };
		return { value: el.value, selectionStart: el.selectionStart, selectionEnd: el.selectionEnd };
	}

	function apply(result: EditState) {
		value = result.value;
		queueMicrotask(() => {
			if (!textarea) return;
			textarea.selectionStart = result.selectionStart;
			textarea.selectionEnd = result.selectionEnd;
			textarea.focus();
		});
	}

	function run(fn: (s: EditState) => EditState | null) {
		const result = fn(currentState());
		if (result) apply(result);
	}

	const actions = {
		bold: () => run((s) => insertSyntaxAroundSelection(s, '**')),
		italic: () => run((s) => insertSyntaxAroundSelection(s, '*')),
		inlineCode: () => run((s) => insertSyntaxAroundSelection(s, '`')),
		link: () => run((s) => handleShortcut(s, 'k', false)),
		bulletList: () => run((s) => insertLinePrefix(s, '- ')),
		numberedList: () => run((s) => insertLinePrefix(s, '1. ')),
		h1: () => run((s) => prefixCurrentLine(s, '# ')),
		h2: () => run((s) => prefixCurrentLine(s, '## ')),
		h3: () => run((s) => prefixCurrentLine(s, '### ')),
		codeBlock: () => run((s) => insertFencedCodeBlock(s)),
		quote: () => run((s) => insertLinePrefix(s, '> ')),
		strike: () => run((s) => insertSyntaxAroundSelection(s, '~~')),
		hr: () => run((s) => insertHorizontalRule(s)),
		checklist: () => run((s) => insertCheckboxList(s)),
		table: () => run((s) => insertTable(s))
	};

	function onkeydown(e: KeyboardEvent) {
		const mod = e.metaKey || e.ctrlKey;
		if (mod) {
			const result = handleShortcut(currentState(), e.key, e.shiftKey);
			if (result) {
				e.preventDefault();
				apply(result);
			}
			return;
		}
		if (e.key === 'Tab') {
			const result = handleTab(currentState(), e.shiftKey);
			if (result) {
				e.preventDefault();
				apply(result);
			}
			return;
		}
		if (e.key === 'Enter' && !e.shiftKey && !e.altKey) {
			const result = handleEnter(currentState());
			if (result) {
				e.preventDefault();
				apply(result);
			}
		}
	}
</script>

<div class={className} data-slot="markdown-editor">
	<Tooltip.Provider>
		<div class="flex flex-col gap-2">
			<!-- Toolbar -->
			<div
				class="flex flex-wrap items-center gap-1 rounded-lg border border-input bg-muted/40 p-1"
				role="toolbar"
				aria-label="Formatting"
			>
				<Tooltip.Root>
					<Tooltip.Trigger
						class={cn(buttonVariants({ variant: 'ghost', size: 'icon' }), 'size-7')}
						onclick={actions.bold}
						aria-label="Bold (Ctrl+B)"
						disabled={mode === 'preview'}
					>
						<BoldIcon />
					</Tooltip.Trigger>
					<Tooltip.Content>Bold <span class="opacity-60">Ctrl+B</span></Tooltip.Content>
				</Tooltip.Root>

				<Tooltip.Root>
					<Tooltip.Trigger
						class={cn(buttonVariants({ variant: 'ghost', size: 'icon' }), 'size-7')}
						onclick={actions.italic}
						aria-label="Italic (Ctrl+I)"
						disabled={mode === 'preview'}
					>
						<ItalicIcon />
					</Tooltip.Trigger>
					<Tooltip.Content>Italic <span class="opacity-60">Ctrl+I</span></Tooltip.Content>
				</Tooltip.Root>

				<Tooltip.Root>
					<Tooltip.Trigger
						class={cn(buttonVariants({ variant: 'ghost', size: 'icon' }), 'size-7')}
						onclick={actions.strike}
						aria-label="Strikethrough"
						disabled={mode === 'preview'}
					>
						<StrikethroughIcon />
					</Tooltip.Trigger>
					<Tooltip.Content>Strikethrough</Tooltip.Content>
				</Tooltip.Root>

				<Tooltip.Root>
					<Tooltip.Trigger
						class={cn(buttonVariants({ variant: 'ghost', size: 'icon' }), 'size-7')}
						onclick={actions.inlineCode}
						aria-label="Inline code (Ctrl+E)"
						disabled={mode === 'preview'}
					>
						<CodeIcon />
					</Tooltip.Trigger>
					<Tooltip.Content>Inline code <span class="opacity-60">Ctrl+E</span></Tooltip.Content>
				</Tooltip.Root>

				<Tooltip.Root>
					<Tooltip.Trigger
						class={cn(buttonVariants({ variant: 'ghost', size: 'icon' }), 'size-7')}
						onclick={actions.link}
						aria-label="Link (Ctrl+K)"
						disabled={mode === 'preview'}
					>
						<LinkIcon />
					</Tooltip.Trigger>
					<Tooltip.Content>Link <span class="opacity-60">Ctrl+K</span></Tooltip.Content>
				</Tooltip.Root>

				<Separator orientation="vertical" class="mx-1 h-5" />

				<Tooltip.Root>
					<Tooltip.Trigger
						class={cn(buttonVariants({ variant: 'ghost', size: 'icon' }), 'size-7')}
						onclick={actions.h1}
						aria-label="Heading 1"
						disabled={mode === 'preview'}
					>
						<Heading1Icon />
					</Tooltip.Trigger>
					<Tooltip.Content>Heading 1</Tooltip.Content>
				</Tooltip.Root>

				<Tooltip.Root>
					<Tooltip.Trigger
						class={cn(buttonVariants({ variant: 'ghost', size: 'icon' }), 'size-7')}
						onclick={actions.h2}
						aria-label="Heading 2"
						disabled={mode === 'preview'}
					>
						<Heading2Icon />
					</Tooltip.Trigger>
					<Tooltip.Content>Heading 2</Tooltip.Content>
				</Tooltip.Root>

				<Tooltip.Root>
					<Tooltip.Trigger
						class={cn(buttonVariants({ variant: 'ghost', size: 'icon' }), 'size-7')}
						onclick={actions.h3}
						aria-label="Heading 3"
						disabled={mode === 'preview'}
					>
						<Heading3Icon />
					</Tooltip.Trigger>
					<Tooltip.Content>Heading 3</Tooltip.Content>
				</Tooltip.Root>

				<Separator orientation="vertical" class="mx-1 h-5" />

				<Tooltip.Root>
					<Tooltip.Trigger
						class={cn(buttonVariants({ variant: 'ghost', size: 'icon' }), 'size-7')}
						onclick={actions.bulletList}
						aria-label="Bullet list"
						disabled={mode === 'preview'}
					>
						<ListIcon />
					</Tooltip.Trigger>
					<Tooltip.Content>Bullet list</Tooltip.Content>
				</Tooltip.Root>

				<Tooltip.Root>
					<Tooltip.Trigger
						class={cn(buttonVariants({ variant: 'ghost', size: 'icon' }), 'size-7')}
						onclick={actions.numberedList}
						aria-label="Numbered list"
						disabled={mode === 'preview'}
					>
						<ListOrderedIcon />
					</Tooltip.Trigger>
					<Tooltip.Content>Numbered list</Tooltip.Content>
				</Tooltip.Root>

				<Tooltip.Root>
					<Tooltip.Trigger
						class={cn(buttonVariants({ variant: 'ghost', size: 'icon' }), 'size-7')}
						onclick={actions.checklist}
						aria-label="Checkbox list"
						disabled={mode === 'preview'}
					>
						<ListChecksIcon />
					</Tooltip.Trigger>
					<Tooltip.Content>Checkbox list</Tooltip.Content>
				</Tooltip.Root>

				<Tooltip.Root>
					<Tooltip.Trigger
						class={cn(buttonVariants({ variant: 'ghost', size: 'icon' }), 'size-7')}
						onclick={actions.quote}
						aria-label="Blockquote"
						disabled={mode === 'preview'}
					>
						<QuoteIcon />
					</Tooltip.Trigger>
					<Tooltip.Content>Blockquote</Tooltip.Content>
				</Tooltip.Root>

				<Separator orientation="vertical" class="mx-1 h-5" />

				<Tooltip.Root>
					<Tooltip.Trigger
						class={cn(buttonVariants({ variant: 'ghost', size: 'icon' }), 'size-7')}
						onclick={actions.codeBlock}
						aria-label="Fenced code block (Ctrl+Shift+K)"
						disabled={mode === 'preview'}
					>
						<CodeIcon class="font-bold" />
					</Tooltip.Trigger>
					<Tooltip.Content>Code block <span class="opacity-60">Ctrl+Shift+K</span></Tooltip.Content>
				</Tooltip.Root>

				<Tooltip.Root>
					<Tooltip.Trigger
						class={cn(buttonVariants({ variant: 'ghost', size: 'icon' }), 'size-7')}
						onclick={actions.hr}
						aria-label="Horizontal rule"
						disabled={mode === 'preview'}
					>
						<MinusIcon />
					</Tooltip.Trigger>
					<Tooltip.Content>Horizontal rule</Tooltip.Content>
				</Tooltip.Root>

				<Tooltip.Root>
					<Tooltip.Trigger
						class={cn(buttonVariants({ variant: 'ghost', size: 'icon' }), 'size-7')}
						onclick={actions.table}
						aria-label="Insert table"
						disabled={mode === 'preview'}
					>
						<TableIcon />
					</Tooltip.Trigger>
					<Tooltip.Content>Table</Tooltip.Content>
				</Tooltip.Root>

				<!-- Edit / Preview toggle -->
				<Button
					variant="ghost"
					size="sm"
					onclick={toggleMode}
					class="ml-auto gap-1.5"
					aria-pressed={mode === 'preview'}
				>
					{#if mode === 'edit'}
						<EyeIcon data-icon="inline-start" />
						Preview
					{:else}
						<PencilIcon data-icon="inline-start" />
						Edit
					{/if}
				</Button>
			</div>

			<!-- Editor / preview (swapping) -->
			{#if mode === 'edit'}
				<div class="flex flex-col gap-1.5">
					<label for={id} class={`text-sm font-medium ${labelSrOnly ? 'sr-only' : ''}`}>{label}</label>
					<textarea
						bind:this={textarea}
						{id}
						bind:value
						{placeholder}
						{rows}
						onkeydown={onkeydown}
						class="rounded-lg border border-input bg-transparent px-2.5 py-2 text-base leading-6 transition-colors focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 outline-none placeholder:text-muted-foreground md:text-sm dark:bg-input/30 min-h-96 w-full flex field-sizing-content font-mono"
					></textarea>
				</div>
			{:else}
				<div class="flex flex-col gap-1.5">
					<span class="sr-only">Preview</span>
					<div class={previewClass} aria-label="Preview">
						{#if value.trim()}
							<MarkdownView content={value} />
						{:else}
							<p class="text-muted-foreground text-sm">Nothing to preview.</p>
						{/if}
					</div>
				</div>
			{/if}
		</div>
	</Tooltip.Provider>
</div>
