import { afterEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/svelte';
import CloseReopenDialog from './CloseReopenDialog.svelte';
import { username } from '$lib/stores/username.svelte';

describe('CloseReopenDialog', () => {
	afterEach(() => username.clear());

	it('keeps the confirm button disabled until a comment is provided', async () => {
		username.set('bob');
		render(CloseReopenDialog, { props: { open: true, status: 'open', onConfirm: vi.fn() } });
		const confirm = screen.getByRole('button', { name: /^close issue$/i });
		expect(confirm).toBeDisabled();
		await fireEvent.input(screen.getByLabelText(/resolution comment/i), {
			target: { value: 'resolved' }
		});
		expect(confirm).not.toBeDisabled();
	});

	it('shows a markdown editor with toolbar', () => {
		render(CloseReopenDialog, { props: { open: true, status: 'open', onConfirm: vi.fn() } });
		expect(screen.getByRole('toolbar', { name: 'Formatting' })).toBeInTheDocument();
	});

	it('calls onConfirm with the comment and pre-filled username on close', async () => {
		username.set('bob');
		const onConfirm = vi.fn().mockResolvedValue(undefined);
		render(CloseReopenDialog, { props: { open: true, status: 'open', onConfirm } });
		await fireEvent.input(screen.getByLabelText(/resolution comment/i), {
			target: { value: 'resolved' }
		});
		await fireEvent.click(screen.getByRole('button', { name: /^close issue$/i }));
		await waitFor(() => expect(onConfirm).toHaveBeenCalledWith('resolved', 'bob'));
	});

	it('titles the dialog for reopen when the issue is closed', () => {
		render(CloseReopenDialog, { props: { open: true, status: 'closed', onConfirm: vi.fn() } });
		expect(screen.getByRole('heading', { name: 'Reopen issue' })).toBeInTheDocument();
	});
});

describe('CloseReopenDialog width', () => {
	it('scales fluidly with the viewport, capped narrower than the create-issue dialog', () => {
		render(CloseReopenDialog, { props: { open: true, status: 'open', onConfirm: vi.fn() } });

		const content = document.querySelector('[data-slot="dialog-content"]');
		// The cap is one fluid rule — viewport-relative, capped at 36rem: wider
		// than the old fixed 28rem cap, narrower than the create-issue dialog's
		// 42rem.
		expect(content).toHaveClass('sm:max-w-[min(calc(100%-2rem),36rem)]');
		// Small screens keep the primitive's untouched default: near-full-width
		// with 1rem gutters.
		expect(content).toHaveClass('max-w-[calc(100%-2rem)]');
		// The old stepped cap must not survive the merge — it would shrink the
		// dialog back to 28rem from the sm breakpoint up.
		expect(content).not.toHaveClass('sm:max-w-md');
	});
});

describe('CloseReopenDialog scrolling layout', () => {
	it('caps the dialog height and scrolls the form body under a fixed header', () => {
		render(CloseReopenDialog, { props: { open: true, status: 'open', onConfirm: vi.fn() } });

		const content = document.querySelector('[data-slot="dialog-content"]');
		if (!content) throw new Error('dialog content did not render');
		// The height cap keeps the dialog an overlay: it can never grow past
		// the viewport, however tall the form gets.
		expect(content).toHaveClass('max-h-[85vh]');
		// The content is a vertical flex column, so the header and the body are
		// independent boxes rather than one scrolling stack.
		expect(content).toHaveClass('flex', 'flex-col');

		// The form is the scrolling body. min-h-0 is what lets it shrink below
		// its content height, so overflow-y engages only when the form is
		// taller than the space left under the header; a fitting form renders
		// without a scrollbar.
		const form = content.querySelector('form');
		if (!form) throw new Error('dialog form did not render');
		expect(form).toHaveClass('flex-1', 'min-h-0', 'overflow-y-auto');

		// The header block sits outside the scrolling body, so the title and
		// the close button stay visible and fixed while the body scrolls.
		const header = content.querySelector('[data-slot="dialog-header"]');
		expect(header).not.toBeNull();
		expect(form.contains(header)).toBe(false);
	});
});
