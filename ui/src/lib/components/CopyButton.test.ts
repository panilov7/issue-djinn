import { fireEvent, render, screen } from '@testing-library/svelte';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { copyToClipboard } from '$lib/utils/clipboard';
import CopyButton from './CopyButton.svelte';

vi.mock('$lib/utils/clipboard', () => ({
	copyToClipboard: vi.fn()
}));

const toast = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('svelte-sonner', () => ({ toast }));

const copyMock = vi.mocked(copyToClipboard);

describe('CopyButton', () => {
	beforeEach(() => {
		toast.success.mockClear();
		toast.error.mockClear();
	});

	it('copies the value on click and confirms with a toast', async () => {
		copyMock.mockResolvedValue(true);
		render(CopyButton, {
			value: 'pi mcp add issue-djinn http://localhost:5444/mcp',
			label: 'pi'
		});

		await fireEvent.click(screen.getByRole('button', { name: /copy pi/i }));

		expect(copyMock).toHaveBeenCalledWith('pi mcp add issue-djinn http://localhost:5444/mcp');
		expect(toast.success).toHaveBeenCalledWith('Copied to clipboard');
	});

	it('reports failure when the clipboard write fails', async () => {
		copyMock.mockResolvedValue(false);
		render(CopyButton, { value: 'claude mcp add issue-djinn http://localhost:5444/mcp', label: 'Endpoint' });

		await fireEvent.click(screen.getByRole('button', { name: /copy endpoint/i }));

		expect(toast.error).toHaveBeenCalled();
		expect(toast.success).not.toHaveBeenCalled();
	});
});
