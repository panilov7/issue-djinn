import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { copyToClipboard } from './clipboard';

describe('copyToClipboard', () => {
	const originalClipboard = globalThis.navigator?.clipboard;
	const originalExecCommand = document.execCommand;

	beforeEach(() => {
		vi.restoreAllMocks();
	});

	afterEach(() => {
		if (originalClipboard) {
			Object.defineProperty(globalThis.navigator, 'clipboard', {
				value: originalClipboard,
				configurable: true
			});
		}
		document.execCommand = originalExecCommand;
	});

	it('uses the Clipboard API when available', async () => {
		const writeText = vi.fn().mockResolvedValue(undefined);
		Object.defineProperty(globalThis.navigator, 'clipboard', {
			value: { writeText },
			configurable: true
		});

		await expect(copyToClipboard('claude mcp add issue-djinn http://localhost:5444/mcp')).resolves.toBe(
			true
		);
		expect(writeText).toHaveBeenCalledWith('claude mcp add issue-djinn http://localhost:5444/mcp');
	});

	it('returns false when the Clipboard API rejects and the fallback fails', async () => {
		Object.defineProperty(globalThis.navigator, 'clipboard', {
			value: { writeText: vi.fn().mockRejectedValue(new Error('denied')) },
			configurable: true
		});
		document.execCommand = vi.fn(() => false) as unknown as typeof document.execCommand;

		await expect(copyToClipboard('text')).resolves.toBe(false);
	});

	it('falls back to execCommand when the Clipboard API is unavailable', async () => {
		Object.defineProperty(globalThis.navigator, 'clipboard', {
			value: undefined,
			configurable: true
		});
		document.execCommand = vi.fn(() => true) as unknown as typeof document.execCommand;

		await expect(copyToClipboard('opencode mcp add issue-djinn http://localhost:5444/mcp')).resolves.toBe(
			true
		);
		expect(document.execCommand).toHaveBeenCalledWith('copy');
	});
});
