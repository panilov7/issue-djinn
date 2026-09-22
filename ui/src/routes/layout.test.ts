import { render } from '@testing-library/svelte';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import LayoutFixture from './layout.test.fixture.svelte';

// The layout reads the router state for its nav highlighting; a fixed value
// keeps the fixture renderable outside a live SvelteKit app.
const page = vi.hoisted(() => ({
	subscribe: vi.fn((listener: (value: { url: { pathname: string } }) => void) => {
		listener({ url: { pathname: '/' } });
		return () => {};
	})
}));
vi.mock('$app/stores', () => ({ page }));

vi.mock('$app/paths', () => ({ resolve: (path: string) => path }));

// jsdom has no EventSource, so the production wiring is observed by replacing
// the global with a recording stand-in: the layout binds the native
// constructor itself, and this asserts it against the right URL.
class RecordingEventSource {
	static constructedUrls: string[] = [];

	constructor(url: string) {
		RecordingEventSource.constructedUrls.push(url);
	}

	addEventListener(): void {}
	close(): void {}
}

describe('Root layout SSE connection', () => {
	// mode-watcher and the sonner Toaster query the color scheme; jsdom has no
	// matchMedia, so the query is stubbed to "light" with no change events.
	beforeEach(() => {
		vi.stubGlobal(
			'matchMedia',
			vi.fn().mockReturnValue({
				matches: false,
				addEventListener: vi.fn(),
				removeEventListener: vi.fn(),
				addListener: vi.fn(),
				removeListener: vi.fn()
			})
		);
	});

	afterEach(() => vi.unstubAllGlobals());

	it("opens the tab's one EventSource against /api/events, once across remounts", () => {
		vi.stubGlobal('EventSource', RecordingEventSource);

		const first = render(LayoutFixture);
		first.unmount();
		render(LayoutFixture);

		// One connection per tab: the layout opens it, pages never do, and a
		// remount of the layout must not open a second one.
		expect(RecordingEventSource.constructedUrls).toEqual(['/api/events']);
	});
});