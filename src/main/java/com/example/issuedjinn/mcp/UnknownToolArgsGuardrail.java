package com.example.issuedjinn.mcp;

import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.ToolInputGuardrail;
import io.quarkiverse.mcp.server.ToolInputGuardrail.ToolInputContext;
import io.quarkiverse.mcp.server.ToolManager.ToolArgument;
import jakarta.enterprise.context.Dependent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rejects tool calls carrying arguments the tool does not declare.
 * The MCP layer binds arguments by name and silently ignores unknown keys, so a
 * client-side rename (e.g. {@code parentId} instead of {@code parent_id}) would
 * drop the value without any error — the exact failure this guardrail exists to prevent.
 *
 * Applied per tool via {@code @ToolGuardrails(input = UnknownToolArgsGuardrail.class)};
 * every {@code @Tool} method in {@link IssueMcpTools} must declare it
 * (pinned by {@code IssueMcpToolsTest#testEveryToolDeclaresUnknownArgsGuardrail}).
 */
@Dependent
public class UnknownToolArgsGuardrail implements ToolInputGuardrail {

    @Override
    public void apply(ToolInputContext context) {
        Set<String> declared = context.getTool().arguments().stream()
                .map(ToolArgument::name)
                .collect(Collectors.toSet());

        List<String> unknown = new ArrayList<>(context.getArguments().fieldNames());
        unknown.removeAll(declared);
        if (unknown.isEmpty()) {
            return;
        }

        String accepted = declared.stream().sorted().collect(Collectors.joining(", "));
        throw new ToolCallException("Unknown argument(s): " + String.join(", ", unknown)
                + ". " + context.getTool().name() + " accepts: " + accepted);
    }
}
