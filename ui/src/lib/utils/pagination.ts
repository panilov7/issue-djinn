export function paginationRange(current: number, totalPages: number): (number | 'ellipsis')[] {
	if (totalPages <= 7) return range(1, totalPages);
	if (current <= 4) return [1, 2, 3, 4, 5, 'ellipsis', totalPages];
	if (current >= totalPages - 3)
		return [
			1,
			'ellipsis',
			totalPages - 4,
			totalPages - 3,
			totalPages - 2,
			totalPages - 1,
			totalPages
		];
	return [1, 'ellipsis', current - 1, current, current + 1, 'ellipsis', totalPages];
}

function range(start: number, end: number): number[] {
	return Array.from({ length: end - start + 1 }, (_, i) => start + i);
}
