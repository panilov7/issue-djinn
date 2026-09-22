import { describe, expect, it } from 'vitest';
import {
	normalizeLineEndings,
	previewHtml,
	insertSyntaxAroundSelection,
	insertLinePrefix,
	prefixCurrentLine,
	insertFencedCodeBlock,
	insertHorizontalRule,
	insertCheckboxList,
	insertTable,
	handleTab,
	handleShiftTab,
	handleEnter,
	handleShortcut,
	insertLink,
	type EditState
} from './markdown-editor';

function sel(value: string, start: number, end: number = start): EditState {
	return { value, selectionStart: start, selectionEnd: end };
}

describe('normalizeLineEndings', () => {
	it('converts CRLF to LF', () => {
		expect(normalizeLineEndings('a\r\nb')).toBe('a\nb');
	});
	it('converts a lone CR to LF', () => {
		expect(normalizeLineEndings('a\rb')).toBe('a\nb');
	});
	it('leaves LF unchanged', () => {
		expect(normalizeLineEndings('a\nb')).toBe('a\nb');
	});
});

describe('previewHtml', () => {
	it('renders markdown to sanitized HTML', () => {
		expect(previewHtml('# Hello')).toContain('<h1');
	});
	it('strips unsafe tags', () => {
		expect(previewHtml('<script>alert(1)</script>')).not.toContain('<script');
	});
});

describe('insertSyntaxAroundSelection', () => {
	it('wraps a selection with the marker and keeps it selected', () => {
		const r = insertSyntaxAroundSelection(sel('hello world', 0, 5), '**');
		expect(r.value).toBe('**hello** world');
		expect(r.selectionStart).toBe(2);
		expect(r.selectionEnd).toBe(7);
	});

	it('inserts empty markers and places the cursor between them when no selection', () => {
		const r = insertSyntaxAroundSelection(sel('hello', 2), '`');
		expect(r.value).toBe('he``llo');
		expect(r.selectionStart).toBe(3);
		expect(r.selectionEnd).toBe(3);
	});

	it('supports asymmetric markers', () => {
		const r = insertSyntaxAroundSelection(sel('foo', 0, 3), '**', '**');
		expect(r.value).toBe('**foo**');
	});
});

describe('insertLinePrefix', () => {
	it('prefixes every line in a multi-line selection', () => {
		const r = insertLinePrefix(sel('a\nb\nc', 0, 5), '> ');
		expect(r.value).toBe('> a\n> b\n> c');
	});

	it('prefixes the single line containing the cursor', () => {
		const r = insertLinePrefix(sel('a\nb\nc', 2, 2), '- ');
		expect(r.value).toBe('a\n- b\nc');
	});
});

describe('prefixCurrentLine', () => {
	it('inserts the prefix at the line start and moves the cursor past it', () => {
		const r = prefixCurrentLine(sel('one\ntwo\nthree', 4, 7), '### ');
		expect(r.value).toBe('one\n### two\nthree');
		expect(r.selectionStart).toBe(8);
	});
});

describe('insertFencedCodeBlock', () => {
	it('inserts a fenced block and selects the (empty) body region', () => {
		const r = insertFencedCodeBlock(sel('foo', 3), 'js');
		expect(r.value).toBe('foo```js\n\n```\n');
		expect(r.selectionStart).toBe(3 + '```js\n'.length);
		expect(r.selectionEnd).toBe(3 + '```js\n'.length);
	});

	it('wraps a selection as the code body', () => {
		const r = insertFencedCodeBlock(sel('foo bar baz', 4, 7), '');
		expect(r.value).toBe('foo ```\nbar\n```\n baz');
		expect(r.selectionStart).toBe(4 + '```\n'.length);
		expect(r.selectionEnd).toBe(4 + '```\n'.length + 3);
	});
});

describe('insertHorizontalRule', () => {
	it('inserts a rule on its own line', () => {
		const r = insertHorizontalRule(sel('hello', 5));
		expect(r.value).toBe('hello\n---\n');
	});
});

describe('insertCheckboxList', () => {
	it('prefixes the line with - [ ] ', () => {
		const r = insertCheckboxList(sel('task one\ntask two', 0, 8));
		expect(r.value).toBe('- [ ] task one\ntask two');
	});
});

describe('insertTable', () => {
	it('inserts a 2x2 table skeleton', () => {
		const r = insertTable(sel('x', 1));
		expect(r.value).toContain('| Column 1 | Column 2 |');
		expect(r.value).toContain('| --- | --- |');
		expect(r.value).toContain('| Cell | Cell |');
	});
});

describe('handleTab', () => {
	it('inserts two spaces at the cursor (not focus-stealing)', () => {
		const r = handleTab(sel('abc', 1), false);
		expect(r).not.toBeNull();
		expect(r!.value).toBe('a  bc');
		expect(r!.selectionStart).toBe(3);
	});

	it('indents every line of a multi-line selection', () => {
		const r = handleTab(sel('a\nb\nc', 0, 5), false);
		expect(r!.value).toBe('  a\n  b\n  c');
	});
});

describe('handleShiftTab', () => {
	it('outdents two leading spaces', () => {
		const r = handleShiftTab(sel('  a\n  b', 0, 7));
		expect(r).not.toBeNull();
		expect(r!.value).toBe('a\nb');
	});

	it('returns null when there is nothing to outdent', () => {
		const r = handleShiftTab(sel('a\nb', 0, 3));
		expect(r).toBeNull();
	});
});

describe('handleEnter', () => {
	it('continues an unordered list item', () => {
		const r = handleEnter(sel('- task', 6));
		expect(r!.value).toBe('- task\n- ');
		expect(r!.selectionStart).toBe(9);
	});

	it('continues and increments an ordered list item', () => {
		const r = handleEnter(sel('1. first', 8));
		expect(r!.value).toBe('1. first\n2. ');
	});

	it('continues a checkbox list item', () => {
		const r = handleEnter(sel('- [ ] thing', 11));
		expect(r!.value).toBe('- [ ] thing\n- [ ] ');
	});

	it('removes an empty list marker (exit the list)', () => {
		const r = handleEnter(sel('- ', 2));
		expect(r!.value).toBe('');
		expect(r!.selectionStart).toBe(0);
	});

	it('returns null for a plain line with no list prefix', () => {
		const r = handleEnter(sel('just text', 4));
		expect(r).toBeNull();
	});

	it('preserves indentation inside a fenced code block', () => {
		// Cursor at end of the `  indented` line, before the trailing newline.
		const src = '```\n  indented\n';
		const r = handleEnter(sel(src, src.length - 1));
		expect(r!.value).toBe('```\n  indented\n  \n');
	});
});

describe('handleShortcut', () => {
	it('Cmd+B wraps selection in **', () => {
		const r = handleShortcut(sel('hi', 0, 2), 'b', false);
		expect(r!.value).toBe('**hi**');
	});

	it('Cmd+I wraps selection in *', () => {
		const r = handleShortcut(sel('hi', 0, 2), 'i', false);
		expect(r!.value).toBe('*hi*');
	});

	it('Cmd+E wraps selection in `', () => {
		const r = handleShortcut(sel('hi', 0, 2), 'e', false);
		expect(r!.value).toBe('`hi`');
	});

	it('Cmd+K inserts a link with the selection as text and selects url', () => {
		const r = handleShortcut(sel('docs', 0, 4), 'k', false);
		expect(r!.value).toBe('[docs](url)');
		expect(r!.selectionStart).toBe(r!.value.length - 4);
		expect(r!.selectionEnd).toBe(r!.value.length - 1);
	});

	it('Cmd+Shift+K inserts a fenced code block', () => {
		const r = handleShortcut(sel('code', 0), 'k', true);
		expect(r!.value).toContain('```\n');
	});

	it('returns null for unbound keys', () => {
		expect(handleShortcut(sel('x', 0), 'z', false)).toBeNull();
	});
});

describe('insertLink', () => {
	it('with no selection, inserts [](url) and places cursor in the brackets', () => {
		const r = insertLink(sel('hello', 2));
		expect(r.value).toBe('he[](url)llo');
		expect(r.selectionStart).toBe(3);
		expect(r.selectionEnd).toBe(3);
	});
});
