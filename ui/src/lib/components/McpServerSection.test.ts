import { fireEvent, render, screen } from '@testing-library/svelte';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import McpServerSection from './McpServerSection.svelte';

const clipboard = vi.hoisted(() => ({ copyToClipboard: vi.fn().mockResolvedValue(true) }));
vi.mock('$lib/utils/clipboard', () => clipboard);

const toast = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('svelte-sonner', () => ({ toast }));

const expectedConfig = `{
  "mcpServers": {
    "issue-djinn": {
      "type": "http",
      "url": "${window.location.origin}/mcp"
    }
  }
}`;

describe('McpServerSection', () => {
	beforeEach(() => {
		toast.success.mockClear();
	});

	it('renders the section title and description', () => {
		render(McpServerSection);
		expect(screen.getByText('MCP Server')).toBeInTheDocument();
		expect(screen.getByText(/Model Context Protocol/i)).toBeInTheDocument();
	});

	it('instructs the user to create a .mcp.json file in the project root', () => {
		render(McpServerSection);
		expect(screen.getAllByText(/\.mcp\.json/).length).toBeGreaterThan(0);
		expect(screen.getByText(/project root/i)).toBeInTheDocument();
	});

	it('shows the .mcp.json snippet with the URL derived from the current origin', () => {
		const { container } = render(McpServerSection);
		expect(container.querySelector('pre code')?.textContent).toBe(expectedConfig);
	});

	it('notes compatibility with Claude Code, GitHub Copilot CLI, and other .mcp.json tools', () => {
		render(McpServerSection);
		expect(screen.getByText(/Claude Code and GitHub Copilot CLI/)).toBeInTheDocument();
	});

	it('copies the snippet from its copy button', async () => {
		render(McpServerSection);
		await fireEvent.click(screen.getByRole('button', { name: /copy \.mcp\.json/i }));
		expect(clipboard.copyToClipboard).toHaveBeenCalledWith(expectedConfig);
		expect(toast.success).toHaveBeenCalledWith('Copied to clipboard');
	});

	it('does not show per-harness tabs or a standalone endpoint display', () => {
		render(McpServerSection);
		expect(screen.queryByRole('tab')).not.toBeInTheDocument();
		expect(screen.queryByText('Endpoint')).not.toBeInTheDocument();
	});
});
