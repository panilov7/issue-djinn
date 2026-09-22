// Vitest setup — runs before every test file.
// Adds jest-dom matchers and polyfills jsdom gaps that the shadcn-svelte
// (Bits UI) primitives rely on: `Element.animate` and `ResizeObserver`.

import '@testing-library/jest-dom/vitest';
import { afterAll } from 'vitest';

class ResizeObserverMock {
	observe(): void {}
	unobserve(): void {}
	disconnect(): void {}
}

if (typeof globalThis.ResizeObserver === 'undefined') {
	globalThis.ResizeObserver = ResizeObserverMock as unknown as typeof ResizeObserver;
}

class AnimationMock {
	onfinish: (() => void) | null = null;
	oncancel: (() => void) | null = null;
	play(): void {}
	pause(): void {}
	finish(): void {}
	cancel(): void {}
	reverse(): void {}
	addEventListener(): void {}
	removeEventListener(): void {}
}

if (typeof Element.prototype.animate === 'undefined') {
	// `Element.animate` is not implemented in jsdom; Bits UI may invoke it.
	// Provide a no-op that returns a minimal `Animation`-like object.
	Element.prototype.animate = () => new AnimationMock() as Animation;
}

if (typeof Element.prototype.scrollIntoView === 'undefined') {
	// jsdom does not implement scrollIntoView either; Bits UI calls it in its
	// keyboard navigation (e.g. the command list behind the issue pickers).
	Element.prototype.scrollIntoView = () => {};
}

// Bits UI restores the body style with a real 24ms timer once the last scroll
// lock is destroyed (body-scroll-lock.svelte.js). Testing-library's cleanup
// unmounts dialogs in each test's afterEach, so after a file's final test that
// timer is still pending when vitest tears down the jsdom environment — it then
// fires against a deleted `document` and surfaces as an unhandled
// "ReferenceError: document is not defined" flake (issue #69). Waiting it out
// here, after all per-test hooks but while the environment is still alive,
// drains the cleanup deterministically for every file, including dialog tests
// added later.
afterAll(() => new Promise((resolve) => setTimeout(resolve, 50)));
