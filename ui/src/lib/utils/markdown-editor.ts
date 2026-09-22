import { renderMarkdown } from './markdown';

/** Sanitized HTML preview of raw markdown source (the live preview pane). */
export function previewHtml(source: string): string {
	return renderMarkdown(source);
}

/** Normalize CRLF/CR line endings to LF so previews render consistently. */
export function normalizeLineEndings(source: string): string {
	return source.replace(/\r\n?/g, '\n');
}

/**
 * Pure markdown-editing primitives for the MarkdownEditor toolbar, shortcuts,
 * Tab/indent, and Enter list-continuation behaviour. Extracted here so they can
 * be unit-tested directly — the component just wires them to the textarea.
 */

export interface EditState {
	value: string;
	selectionStart: number;
	selectionEnd: number;
}

export type EditResult = EditState;

function clamp(n: number, min: number, max: number): number {
	return Math.max(min, Math.min(max, n));
}

function ensureRange(state: EditState): { start: number; end: number } {
	const start = Math.min(state.selectionStart, state.selectionEnd);
	const end = Math.max(state.selectionStart, state.selectionEnd);
	return { start, end };
}

/**
 * Wrap the current selection with `before` and `after`. With no selection,
 * inserts the markers and places the cursor between them. With a selection,
 * wraps it and keeps it selected.
 */
export function insertSyntaxAroundSelection(
	state: EditState,
	before: string,
	after: string = before
): EditResult {
	const { start, end } = ensureRange(state);
	const selected = state.value.slice(start, end);
	const next = state.value.slice(0, start) + before + selected + after + state.value.slice(end);
	if (start === end) {
		const cursor = start + before.length;
		return { value: next, selectionStart: cursor, selectionEnd: cursor };
	}
	return {
		value: next,
		selectionStart: start + before.length,
		selectionEnd: end + before.length
	};
}

/**
 * Prefix every line intersecting the selection with `prefix`. Returns a result
 * whose selection spans the same lines (expanded by the prefix widths).
 */
export function insertLinePrefix(state: EditState, prefix: string): EditResult {
	const { start, end } = ensureRange(state);
	const lineStart = state.value.lastIndexOf('\n', start - 1) + 1;
	let lineEnd = state.value.indexOf('\n', end);
	if (lineEnd === -1) lineEnd = state.value.length;

	const block = state.value.slice(lineStart, lineEnd);
	const lines = block.split('\n');
	const prefixed = lines.map((l) => prefix + l).join('\n');
	const next = state.value.slice(0, lineStart) + prefixed + state.value.slice(lineEnd);

	const prefixTotal = prefix.length * lines.length;
	return {
		value: next,
		selectionStart: start + prefix.length,
		selectionEnd: end + prefixTotal
	};
}

/**
 * Prefix the line containing the cursor (or the first line of the selection)
 * with `prefix`. Used for single-line block constructs: headings, blockquote,
 * horizontal rule, fenced code block opener.
 */
export function prefixCurrentLine(state: EditState, prefix: string): EditResult {
	const { start, end } = ensureRange(state);
	const lineStart = state.value.lastIndexOf('\n', start - 1) + 1;
	const next = state.value.slice(0, lineStart) + prefix + state.value.slice(lineStart);
	const cursor = end + prefix.length;
	return { value: next, selectionStart: start + prefix.length, selectionEnd: cursor };
}

/**
 * Insert a full fenced code block at the cursor, with the cursor on a blank line
 * inside the fences. If there is a selection, it becomes the block's content.
 */
export function insertFencedCodeBlock(state: EditState, lang = ''): EditResult {
	const { start, end } = ensureRange(state);
	const selected = state.value.slice(start, end);
	const opener = `\`\`\`${lang}\n`;
	const block = `${opener}${selected}\n\`\`\`\n`;
	const next = state.value.slice(0, start) + block + state.value.slice(end);
	const innerStart = start + opener.length;
	return {
		value: next,
		selectionStart: innerStart,
		selectionEnd: innerStart + selected.length
	};
}

/**
 * Insert a horizontal rule on its own line.
 */
export function insertHorizontalRule(state: EditState): EditResult {
	const { start } = ensureRange(state);
	const lineStart = state.value.lastIndexOf('\n', start - 1) + 1;
	const lineEmpty = lineStart === start;
	const rule = lineEmpty ? '---\n' : '\n---\n';
	const insertAt = lineEmpty ? lineStart : start;
	const next = state.value.slice(0, insertAt) + rule + state.value.slice(insertAt);
	const cursor = insertAt + rule.length;
	return { value: next, selectionStart: cursor, selectionEnd: cursor };
}

/**
 * Insert a GFM checkbox/task list item (`- [ ] `) at the start of the current
 * line(s).
 */
export function insertCheckboxList(state: EditState): EditResult {
	return insertLinePrefix(state, '- [ ] ');
}

/**
 * Insert a 2-column, 2-row markdown table skeleton at the cursor.
 */
export function insertTable(state: EditState): EditResult {
	const { start, end } = ensureRange(state);
	const table = '\n| Column 1 | Column 2 |\n| --- | --- |\n| Cell | Cell |\n';
	const next = state.value.slice(0, start) + table + state.value.slice(end);
	const cursor = start + table.length;
	return { value: next, selectionStart: cursor, selectionEnd: cursor };
}

const LIST_PREFIX = /^(\s*)([-*+]\s(?:\[\s\]\s)?|(\d+)\.\s)/;
const ORDERED = /^(\s*)(\d+)\.\s/;
const CHECKBOX = /^(\s*)([-*+]\s)\[\s\]\s/;

/**
 * Handle `Tab` inside the textarea: insert two spaces at the cursor (not focus-
 * stealing). With a multi-line selection, indent every selected line by two
 * spaces.
 */
export function handleTab(state: EditState, shift: boolean): EditResult | null {
	const { start, end } = ensureRange(state);
	if (shift) return handleShiftTab(state);

	if (start !== end) {
		return insertLinePrefix(state, '  ');
	}
	const next = state.value.slice(0, start) + '  ' + state.value.slice(end);
	const cursor = start + 2;
	return { value: next, selectionStart: cursor, selectionEnd: cursor };
}

/**
 * Outdent selected lines by two spaces (or the leading-whitespace width, up to
 * two). Returns null if nothing to outdent.
 */
export function handleShiftTab(state: EditState): EditResult | null {
	const { start, end } = ensureRange(state);
	const lineStart = state.value.lastIndexOf('\n', start - 1) + 1;
	let lineEnd = state.value.indexOf('\n', end);
	if (lineEnd === -1) lineEnd = state.value.length;

	const block = state.value.slice(lineStart, lineEnd);
	const lines = block.split('\n');
	let removed = 0;
	const outdented = lines
		.map((l) => {
			const m = l.match(/^\s{1,2}/);
			if (!m) return l;
			removed += m[0].length;
			return l.slice(m[0].length);
		})
		.join('\n');
	if (removed === 0) return null;
	const next = state.value.slice(0, lineStart) + outdented + state.value.slice(lineEnd);
	return {
		value: next,
		selectionStart: clamp(start - 1, lineStart, next.length),
		selectionEnd: clamp(end - removed, lineStart, next.length)
	};
}

/**
 * Handle `Enter`: auto-continue list items. If the cursor is on a line whose
 * only content is a list marker, remove the marker (empty list item exits the
 * list). Otherwise repeat the marker prefix on the next line, incrementing
 * ordered-list numbers. Inside a fenced code block, just insert a newline and
 * preserve the current line's leading indentation.
 *
 * Returns null to signal "no special handling — let the browser insert \n".
 */
export function handleEnter(state: EditState): EditResult | null {
	const { start, end } = ensureRange(state);
	if (start !== end) return null;

	const lineStart = state.value.lastIndexOf('\n', start - 1) + 1;
	const line = state.value.slice(lineStart, start);

	if (isInsideFencedBlock(state)) {
		const indent = (line.match(/^\s*/) ?? [''])[0];
		const next = state.value.slice(0, start) + '\n' + indent + state.value.slice(end);
		const cursor = start + 1 + indent.length;
		return { value: next, selectionStart: cursor, selectionEnd: cursor };
	}

	const m = line.match(LIST_PREFIX);
	if (!m) return null;
	const prefix = m[0];
	const rest = line.slice(prefix.length);
	if (rest.trim().length === 0) {
		const next = state.value.slice(0, lineStart) + state.value.slice(start);
		return { value: next, selectionStart: lineStart, selectionEnd: lineStart };
	}

	const ordered = prefix.match(ORDERED);
	const checkbox = prefix.match(CHECKBOX);
	let nextPrefix = prefix;
	if (ordered) {
		const n = parseInt(ordered[2], 10);
		nextPrefix = `${ordered[1]}${n + 1}. `;
	} else if (checkbox) {
		nextPrefix = `${checkbox[1]}${checkbox[2]}[ ] `;
	} else {
		const bare = prefix.match(/^(\s*)([-*+]\s)/);
		if (bare) nextPrefix = `${bare[1]}${bare[2]}`;
	}
	const insert = '\n' + nextPrefix;
	const next = state.value.slice(0, start) + insert + state.value.slice(end);
	const cursor = start + insert.length;
	return { value: next, selectionStart: cursor, selectionEnd: cursor };
}

function isInsideFencedBlock(state: EditState): boolean {
	const upto = state.value.slice(0, state.selectionStart);
	const fenceOpens = (upto.match(/^```/gm) ?? []).length;
	return fenceOpens % 2 === 1;
}

const SHORTCUTS: Record<string, (s: EditState) => EditResult> = {
	b: (s) => insertSyntaxAroundSelection(s, '**'),
	i: (s) => insertSyntaxAroundSelection(s, '*'),
	k: (s) => insertLink(s),
	e: (s) => insertSyntaxAroundSelection(s, '`')
};

/**
 * Resolve a `Cmd/Ctrl+<key>` shortcut to an edit result, or null if the key
 * isn't bound. `Shift+K` (fenced code block) is handled separately by the
 * caller via `key === 'k' && shift`.
 */
export function handleShortcut(
	state: EditState,
	key: string,
	shift: boolean
): EditResult | null {
	const k = key.toLowerCase();
	if (k === 'k' && shift) return insertFencedCodeBlock(state);
	if (shift) return null;
	return SHORTCUTS[k] ? SHORTCUTS[k](state) : null;
}

/**
 * Wrap the selection as a markdown link. With a selection, the selection text
 * becomes the link text and the cursor moves to the URL position. With no
 * selection, insert `[](url)` with the cursor between the brackets.
 */
export function insertLink(state: EditState): EditResult {
	const { start, end } = ensureRange(state);
	const selected = state.value.slice(start, end);
	const text = selected.length > 0 ? selected : '';
	const inserted = `[${text}](url)`;
	const next = state.value.slice(0, start) + inserted + state.value.slice(end);
	if (selected.length > 0) {
		const urlStart = start + inserted.length - 4;
		return { value: next, selectionStart: urlStart, selectionEnd: urlStart + 3 };
	}
	const cursor = start + 1;
	return { value: next, selectionStart: cursor, selectionEnd: cursor };
}
