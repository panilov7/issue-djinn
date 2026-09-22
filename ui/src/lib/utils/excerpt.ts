export function makeExcerpt(text: string, max = 160): string {
	const plain = text.replace(/[#`*>_~\-\[\]\(\)]/g, '').replace(/\s+/g, ' ').trim();
	if (plain.length <= max) return plain;
	return plain.slice(0, max).trimEnd() + '…';
}

export function parseLabels(input: string): string[] {
	return [
		...new Set(
			input
				.split(',')
				.map((l) => l.trim())
				.filter((l) => l.length > 0)
		)
	];
}
