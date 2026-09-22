import { render } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import Fixture from './command-input.test.fixture.svelte';

// This component is vendored from the shadcn-svelte registry, and its deliverable
// *is* presentational markup — the magnifier icon and the divider under the input —
// so a markup-level assertion is the honest test here. The registry copy once drifted
// to a bare CommandPrimitive.Input (no icon, no divider); this guards against that.
describe('command-input', () => {
	it('renders the registry wrapper: search icon + divider around the input', () => {
		const { container } = render(Fixture);

		const wrapper = container.querySelector('[data-slot="command-input-wrapper"]');
		expect(wrapper).not.toBeNull();
		expect(wrapper?.classList.contains('border-b')).toBe(true);
		expect(wrapper?.querySelector('svg')).not.toBeNull();
		expect(wrapper?.querySelector('[data-slot="command-input"]')).not.toBeNull();
	});
});