/**
 * Clipboard helpers.
 *
 * `navigator.clipboard` requires a secure context and is absent in some
 * embedders/jsdom. The write falls back to the legacy `document.execCommand`
 * path so copy still works when the Clipboard API is unavailable.
 */

/**
 * Copy `text` to the system clipboard.
 *
 * @returns `true` if the copy succeeded, `false` otherwise.
 */
export async function copyToClipboard(text: string): Promise<boolean> {
	if (typeof navigator !== 'undefined' && navigator.clipboard) {
		try {
			await navigator.clipboard.writeText(text);
			return true;
		} catch {
			// fall through to the legacy path
		}
	}
	return legacyCopy(text);
}

function legacyCopy(text: string): boolean {
	try {
		const textarea = document.createElement('textarea');
		textarea.value = text;
		// Keep it off-screen and non-visible so focus is not disturbed.
		textarea.style.position = 'fixed';
		textarea.style.opacity = '0';
		document.body.appendChild(textarea);
		textarea.select();
		const ok = document.execCommand('copy');
		document.body.removeChild(textarea);
		return ok;
	} catch {
		return false;
	}
}
