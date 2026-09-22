import { fireEvent, render, screen } from '@testing-library/svelte';
import { describe, expect, it, vi } from 'vitest';
import LabelTagInput from './LabelTagInput.svelte';

function makeProps(overrides: Record<string, unknown> = {}) {
	return {
		value: [],
		onchange: vi.fn(),
		...overrides
	};
}

describe('LabelTagInput', () => {
	it('renders an input field', () => {
		render(LabelTagInput, { props: makeProps() });
		expect(screen.getByRole('textbox')).toBeInTheDocument();
	});

	it('shows existing labels as removable tags', () => {
		render(LabelTagInput, { props: makeProps({ value: ['needs-triage', 'task'] }) });
		expect(screen.getByText('needs-triage')).toBeInTheDocument();
		expect(screen.getByText('task')).toBeInTheDocument();
	});

	it('adds a label when typing and pressing Enter', async () => {
		const p = makeProps();
		render(LabelTagInput, { props: p });
		await fireEvent.input(screen.getByRole('textbox'), { target: { value: 'bug' } });
		await fireEvent.keyDown(screen.getByRole('textbox'), { key: 'Enter' });
		expect(p.onchange).toHaveBeenCalledWith(['bug']);
	});

	it('adds a label when typing and pressing comma', async () => {
		const p = makeProps();
		render(LabelTagInput, { props: p });
		await fireEvent.input(screen.getByRole('textbox'), { target: { value: 'enhancement' } });
		await fireEvent.keyDown(screen.getByRole('textbox'), { key: ',' });
		expect(p.onchange).toHaveBeenCalledWith(['enhancement']);
	});

	it('removes a label when clicking its × button', async () => {
		const p = makeProps({ value: ['needs-triage', 'task'] });
		render(LabelTagInput, { props: p });
		// Find the × buttons — there are two (one per tag)
		const buttons = screen.getAllByRole('button', { name: /remove/i });
		await fireEvent.click(buttons[0]);
		expect(p.onchange).toHaveBeenCalledWith(['task']);
	});

	it('removes the last label when pressing Backspace with empty input', async () => {
		const p = makeProps({ value: ['needs-triage', 'task'] });
		render(LabelTagInput, { props: p });
		// Simulate empty input
		await fireEvent.input(screen.getByRole('textbox'), { target: { value: '' } });
		await fireEvent.keyDown(screen.getByRole('textbox'), { key: 'Backspace' });
		expect(p.onchange).toHaveBeenCalledWith(['needs-triage']);
	});

	it('does not add an empty label on Enter with no text', async () => {
		const p = makeProps();
		render(LabelTagInput, { props: p });
		await fireEvent.keyDown(screen.getByRole('textbox'), { key: 'Enter' });
		expect(p.onchange).not.toHaveBeenCalled();
	});

	it('does not add duplicate labels', async () => {
		const p = makeProps({ value: ['bug'] });
		render(LabelTagInput, { props: p });
		await fireEvent.input(screen.getByRole('textbox'), { target: { value: 'bug' } });
		await fireEvent.keyDown(screen.getByRole('textbox'), { key: 'Enter' });
		expect(p.onchange).not.toHaveBeenCalled();
	});
});

// Whatever mounts LabelTagInput submits the labels as one full array
// (CONTEXT.md — labels are a whole-array replacement on PATCH), so picking up
// uncommitted text is the input's job, not either form's. The harness is a
// real <form> element that mirrors the child's value the way IssueForm does,
// because CONTEXT.md keeps test fixtures inline.
describe('LabelTagInput inside a form', () => {
	function renderInForm(props: {
		initialLabels?: string[];
		onchange?: (labels: string[]) => void;
	}) {
		const submitted: string[][] = [];
		let labels = [...(props.initialLabels ?? [])];

		const form = document.createElement('form');
		form.innerHTML = '<button type="submit">Save</button>';
		form.addEventListener('submit', (e) => {
			e.preventDefault();
			// IssueForm reads its own label state when it submits.
			submitted.push([...labels]);
		});
		document.body.appendChild(form);

		function change(next: string[]) {
			labels = next;
			props.onchange?.(next);
			rendered.rerender({ value: next, onchange: change });
		}

		const rendered = render(
			LabelTagInput,
			// `target` mounts the component into the form, so it has a form owner
			// to flush for.
			{ target: form, props: { value: labels, onchange: change } }
		);
		return { submitted, form };
	}

	it('keeps a typed label when the form is submitted without Enter or comma', async () => {
		const { submitted } = renderInForm({});

		const input = screen.getByRole('textbox');
		await fireEvent.input(input, { target: { value: 'bug' } });
		await fireEvent.click(screen.getByRole('button', { name: 'Save' }));

		expect(submitted).toEqual([['bug']]);
		// Consumed, not just submitted — a later blur cannot add it again.
		expect(input).toHaveValue('');
	});

	it('keeps a typed label on a submit event that does not blur the input first', async () => {
		// jsdom does not move focus on click, so this stands in for the
		// keyboard path: the pending text is still in the input when `submit`
		// fires, so committing on blur alone cannot save it.
		const { submitted, form } = renderInForm({});

		await fireEvent.input(screen.getByRole('textbox'), { target: { value: 'bug' } });
		await fireEvent.submit(form);

		expect(submitted).toEqual([['bug']]);
	});

	it('commits pending text when the input loses focus', async () => {
		const onchange = vi.fn();
		renderInForm({ onchange });
		const input = screen.getByRole('textbox');

		await fireEvent.input(input, { target: { value: 'bug' } });
		expect(onchange).not.toHaveBeenCalled();

		await fireEvent.blur(input);
		expect(onchange).toHaveBeenCalledWith(['bug']);
		expect(input).toHaveValue('');
	});

	it('does not commit anything when blurred with no pending text', async () => {
		const onchange = vi.fn();
		renderInForm({ onchange });

		await fireEvent.blur(screen.getByRole('textbox'));

		expect(onchange).not.toHaveBeenCalled();
	});

	it('still ignores a duplicate typed before a submit', async () => {
		const { submitted, form } = renderInForm({ initialLabels: ['bug'] });

		await fireEvent.input(screen.getByRole('textbox'), { target: { value: 'bug' } });
		await fireEvent.submit(form);

		expect(submitted).toEqual([['bug']]);
	});

	it('removes the right tag after a blur commit appended to the end', async () => {
		const onchange = vi.fn();
		renderInForm({ initialLabels: ['bug', 'ui'], onchange });
		const input = screen.getByRole('textbox');

		// Leaving the input (to whatever gets clicked next) commits the pending
		// text; its addition has to leave the earlier tags' indices untouched.
		await fireEvent.input(input, { target: { value: 'perf' } });
		await fireEvent.blur(input);
		await fireEvent.click(screen.getByRole('button', { name: 'Remove ui' }));

		expect(onchange).toHaveBeenNthCalledWith(1, ['bug', 'ui', 'perf']);
		expect(onchange).toHaveBeenNthCalledWith(2, ['bug', 'perf']);
	});
});
