import { fireEvent, render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import MarkdownEditor from './MarkdownEditor.svelte';

describe('MarkdownEditor', () => {
	it('renders a textarea and a formatting toolbar in edit mode', () => {
		render(MarkdownEditor, { props: { value: '# Hello' } });
		expect(screen.getByRole('textbox')).toBeInTheDocument();
		expect(screen.getByRole('toolbar', { name: 'Formatting' })).toBeInTheDocument();
		expect(screen.getByRole('button', { name: 'Bold (Ctrl+B)' })).toBeInTheDocument();
	});

	it('toggles to preview mode and renders the markdown', async () => {
		render(MarkdownEditor, { props: { value: '# Hello' } });
		await fireEvent.click(screen.getByRole('button', { name: 'Preview' }));
		expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
		expect(screen.getByLabelText('Preview').querySelector('h1')).toBeInTheDocument();
	});

	it('applies a toolbar action to the textarea value', async () => {
		render(MarkdownEditor, { props: { value: 'hello' } });
		const textarea = screen.getByRole('textbox') as HTMLTextAreaElement;
		textarea.setSelectionRange(0, 5);
		await fireEvent.click(screen.getByRole('button', { name: 'Bold (Ctrl+B)' }));
		expect(textarea).toHaveValue('**hello**');
	});

	it('applies the Ctrl+B keyboard shortcut to the selection', async () => {
		render(MarkdownEditor, { props: { value: 'hello' } });
		const textarea = screen.getByRole('textbox') as HTMLTextAreaElement;
		textarea.setSelectionRange(0, 5);
		await fireEvent.keyDown(textarea, { key: 'b', ctrlKey: true });
		expect(textarea).toHaveValue('**hello**');
	});

	it('shows the empty-preview state when there is nothing to preview', async () => {
		render(MarkdownEditor, { props: { value: '' } });
		await fireEvent.click(screen.getByRole('button', { name: 'Preview' }));
		expect(screen.getByLabelText('Preview')).toHaveTextContent('Nothing to preview.');
	});
});
