package com.example.issuedjinn.mcp;

import com.example.issuedjinn.domain.service.IssueService;
import com.example.issuedjinn.domain.service.IssueService.IssueWithChildren;
import com.example.issuedjinn.domain.service.IssueService.NotFoundException;
import com.example.issuedjinn.dto.IssueDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.TextResourceContents;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * MCP resource for reading issues by URI template.
 * Provides read-only access to issue data at {@code issue:///{id}}.
 */
@Singleton
public class IssueMcpResources {

    @Inject
    IssueService issueService;

    @Inject
    ObjectMapper objectMapper;

    @ResourceTemplate(
            uriTemplate = "issue:///{id}",
            description = "A single tracked issue, addressed by numeric ID. Its children are embedded oldest-created-first (createdAt asc, id asc), uncapped — the same order get_issue and list_children serve.",
            mimeType = "application/json"
    )
    public TextResourceContents issue(String id, RequestUri uri) {
        Long issueId;
        try {
            issueId = Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new McpException("Invalid issue ID: " + id + ". " + e.getMessage(), JsonRpcErrorCodes.INVALID_PARAMS);
        }

        IssueWithChildren detail;
        try {
            detail = issueService.getIssueWithChildren(issueId);
        } catch (NotFoundException e) {
            throw new McpException(e.getMessage(), JsonRpcErrorCodes.RESOURCE_NOT_FOUND);
        }

        IssueDto dto = IssueDto.fromIssue(detail.issue(), detail.children());
        String json;
        try {
            json = objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new McpException("Failed to serialize issue: " + e.getMessage(),
                    JsonRpcErrorCodes.INTERNAL_ERROR);
        }

        return new TextResourceContents(uri.value(), json, "application/json");
    }
}