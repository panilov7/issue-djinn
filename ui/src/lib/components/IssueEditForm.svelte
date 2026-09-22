<script lang="ts">
	/**
	 * The detail page's inline edit form: a shell over the shared IssueForm core
	 * in edit mode. It exists to name the edit surface and pin what an edit is —
	 * the seeds come from the issue being edited, and an edit never moves an
	 * issue, so the core's parent picker stays hidden.
	 */
	import * as Card from '$lib/components/ui/card';
	import IssueForm, { type IssueFormValues } from './IssueForm.svelte';

	let {
		title,
		description,
		labels,
		onSave,
		onCancel
	}: {
		title: string;
		description: string;
		labels: string[];
		/** Called with the edited fields; a rejection is surfaced as the form's error alert. */
		onSave: (values: IssueFormValues) => void | Promise<void>;
		/** Called when the user dismisses the form. */
		onCancel: () => void;
	} = $props();
</script>

<!-- The card gives edit mode the same visual boundary as view mode, where the
     description sits in a card. -->
<Card.Root>
	<Card.Content>
		<IssueForm
			mode="edit"
			initialTitle={title}
			initialDescription={description}
			initialLabels={labels}
			onSubmit={onSave}
			onCancel={onCancel}
		/>
	</Card.Content>
</Card.Root>
