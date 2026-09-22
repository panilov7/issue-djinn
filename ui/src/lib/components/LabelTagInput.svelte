<script lang="ts">
	let {
		value = [],
		onchange
	}: {
		value: string[];
		onchange?: (value: string[]) => void;
	} = $props();

	let inputValue = $state('');

	function commitLabel(raw: string) {
		const trimmed = raw.trim();
		if (trimmed.length === 0) return;
		if (value.includes(trimmed)) return;
		onchange?.([...value, trimmed]);
	}

	function removeLabel(index: number) {
		onchange?.(value.filter((_, i) => i !== index));
	}

	function removeLastLabel() {
		if (value.length === 0) return;
		onchange?.(value.slice(0, -1));
	}

	/** Adds the typed text as a label and leaves the empty input ready again. */
	function commitAndClear() {
		commitLabel(inputValue);
		inputValue = '';
	}

	function handleKeydown(e: KeyboardEvent) {
		if (e.key === 'Enter' || e.key === ',') {
			e.preventDefault();
			commitAndClear();
		} else if (e.key === 'Backspace' && inputValue === '') {
			removeLastLabel();
		}
	}

	// Typed-but-uncommitted text lives only in the input — `value` still
	// excludes it, and the forms wrapping this one submit a whole label array.
	// So commit it wherever it would otherwise be dropped: on blur (the mouse
	// path — focus leaves the input before `click`) and on the form's `submit`
	// (the keyboard path, which never blurs first).
	function commitPendingLabel() {
		if (inputValue.trim() === '') return;
		commitAndClear();
	}

	/**
	 * Feeds `submit` events of the enclosing form into `commitPendingLabel`.
	 *
	 * The listener sits on `document` in the capture phase because that is the
	 * only ordering we can rely on: the wrapping form handles `submit` at
	 * itself, which always runs after document capture, and a keyboard submit
	 * blurs nothing before it. Scoped to this input's own form so a second
	 * LabelTagInput elsewhere is left alone.
	 */
	function flushOnFormSubmit(node: HTMLElement) {
		// The form is a fixed ancestor of this component, so resolving it once
		// is enough; with no enclosing form there is nothing to flush before.
		const form = node.closest('form');
		if (!form) return;
		function handleSubmit(e: Event) {
			if (e.target !== form) return;
			commitPendingLabel();
		}
		document.addEventListener('submit', handleSubmit, true);
		return {
			destroy() {
				document.removeEventListener('submit', handleSubmit, true);
			}
		};
	}
</script>

<div
	use:flushOnFormSubmit
	class="flex flex-wrap gap-1 rounded-md border border-input bg-transparent px-2 py-1.5 text-sm shadow-sm"
>
	{#each value as label, i (label)}
		<span class="flex items-center gap-0.5 rounded bg-muted px-1.5 py-0.5 text-xs font-medium">
			{label}
			<button
				type="button"
				class="ml-0.5 text-muted-foreground hover:text-foreground"
				aria-label={`Remove ${label}`}
				onclick={() => removeLabel(i)}
			>
				×
			</button>
		</span>
	{/each}
	<input
		type="text"
		class="min-w-24 flex-1 bg-transparent outline-none placeholder:text-muted-foreground"
		placeholder="Add label…"
		aria-label="Add label"
		bind:value={inputValue}
		onkeydown={handleKeydown}
		onblur={commitPendingLabel}
	/>
</div>
