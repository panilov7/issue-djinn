export function relativeTime(iso: string): string {
	const then = new Date(iso).getTime();
	const diff = Date.now() - then;
	const sec = Math.floor(diff / 1000);
	const min = Math.floor(sec / 60);
	const hr = Math.floor(min / 60);
	const day = Math.floor(hr / 24);
	if (sec < 60) return 'just now';
	if (min < 60) return `${min} minute${min === 1 ? '' : 's'} ago`;
	if (hr < 24) return `${hr} hour${hr === 1 ? '' : 's'} ago`;
	if (day < 30) return `${day} day${day === 1 ? '' : 's'} ago`;
	const months = Math.floor(day / 30);
	if (months < 12) return `${months} month${months === 1 ? '' : 's'} ago`;
	const years = Math.floor(day / 365);
	return `${years} year${years === 1 ? '' : 's'} ago`;
}

export function formatTimestamp(iso: string): string {
	return new Date(iso).toLocaleString(undefined, {
		year: 'numeric',
		month: 'short',
		day: 'numeric',
		hour: '2-digit',
		minute: '2-digit'
	});
}
