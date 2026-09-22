package com.example.issuedjinn.rest.api;

import com.example.issuedjinn.domain.entity.Comment;
import com.example.issuedjinn.domain.entity.Issue;
import com.example.issuedjinn.domain.repository.IssueQuery;
import com.example.issuedjinn.domain.service.IssueService;
import com.example.issuedjinn.dto.CommentDto;
import com.example.issuedjinn.dto.DependencyDto;
import com.example.issuedjinn.dto.IssueDto;
import com.example.issuedjinn.dto.IssueListDto;
import com.example.issuedjinn.rest.api.dto.AddDependencyRequest;
import com.example.issuedjinn.rest.api.dto.AddCommentRequest;
import com.example.issuedjinn.rest.api.dto.CloseIssueRequest;
import com.example.issuedjinn.rest.api.dto.CommentsListDto;
import com.example.issuedjinn.rest.api.dto.CreateIssueRequest;
import com.example.issuedjinn.rest.api.dto.IssueSearchParams;
import com.example.issuedjinn.rest.api.dto.ReopenIssueRequest;
import com.example.issuedjinn.rest.api.dto.UpdateIssueRequest;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * REST resource for Issue entities.
 * All business logic is delegated to {@link IssueService}.
 */
@Path("/issues")
@Produces(MediaType.APPLICATION_JSON)
public class IssueResource {

    @Inject
    IssueService issueService;

    /**
     * List issues with optional filters.
     */
    @GET
    public Response listIssues(@BeanParam IssueSearchParams params) {
        IssueQuery query = IssueQuery.fromSearchParams(params);
        List<IssueService.IssueListRow> rows = issueService.listIssues(query);

        return Response.ok(new IssueListDto(IssueListDto.IssueListRowDto.fromRows(rows))).build();
    }

    /**
     * Create a new issue.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createIssue(@Valid CreateIssueRequest request) {
        Issue issue = issueService.createIssue(
                request.title,
                request.description,
                request.parentId,
                request.labels
        );
        IssueDto dto = IssueDto.fromIssue(issue);
        return Response.status(Response.Status.CREATED).entity(dto).build();
    }

    /**
     * Get issue by ID with detail shape, children embedded in the
     * {@code child_direction} order (oldest-created-first by default,
     * ascending or descending — anything else is a 400). The detail endpoint
     * takes no other sort parameter: its rows are the issue itself and its
     * children, not parent-level rows.
     */
    @GET
    @Path("/{id}")
    public Response getIssue(@PathParam("id") Long id,
                             @QueryParam("child_direction") String childDirection) {
        IssueService.IssueWithChildren detail =
                issueService.getIssueWithChildren(id, IssueQuery.childOrderFromParam(childDirection));
        IssueDto dto = IssueDto.fromIssue(detail.issue(), detail.children());
        return Response.ok(dto).build();
    }

    /**
     * Update issue by ID. parentId is presence-aware: absent = keep, explicit
     * null = unparent, integer = set or replace (see {@link UpdateIssueRequest#parentUpdate()}).
     */
    @PATCH
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateIssue(@PathParam("id") Long id, @Valid UpdateIssueRequest request) {
        Issue issue = issueService.updateIssue(
                id,
                request.title,
                request.description,
                request.assignee,
                request.labels != null ? new HashSet<>(request.labels) : null,
                request.parentUpdate()
        );

        IssueDto dto = IssueDto.fromIssue(issue);
        return Response.ok(dto).build();
    }

    /**
     * Close an issue.
     */
    @POST
    @Path("/{id}/close")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response closeIssue(@PathParam("id") Long id, @Valid CloseIssueRequest request) {
        Issue issue = issueService.closeIssue(id, request.comment, request.author);

        Set<DependencyDto> dependencies = issue.dependencies.stream()
                .map(d -> new DependencyDto(d.id, d.title, d.status))
                .collect(Collectors.toSet());

        Set<DependencyDto> dependents = issue.dependents.stream()
                .map(d -> new DependencyDto(d.id, d.title, d.status))
                .collect(Collectors.toSet());

        IssueDto dto = IssueDto.fromIssue(issue);
        return Response.ok(dto).build();
    }

    /**
     * Reopen an issue.
     */
    @POST
    @Path("/{id}/reopen")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response reopenIssue(@PathParam("id") Long id, @Valid ReopenIssueRequest request) {
        Issue issue = issueService.reopenIssue(id, request.comment, request.author);

        Set<DependencyDto> dependencies = issue.dependencies.stream()
                .map(d -> new DependencyDto(d.id, d.title, d.status))
                .collect(Collectors.toSet());

        Set<DependencyDto> dependents = issue.dependents.stream()
                .map(d -> new DependencyDto(d.id, d.title, d.status))
                .collect(Collectors.toSet());

        IssueDto dto = IssueDto.fromIssue(issue);
        return Response.ok(dto).build();
    }

    /**
     * Unassign an issue.
     */
    @POST
    @Path("/{id}/unassign")
    public Response unassignIssue(@PathParam("id") Long id) {
        Issue issue = issueService.unassignIssue(id);

        Set<DependencyDto> dependencies = issue.dependencies.stream()
                .map(d -> new DependencyDto(d.id, d.title, d.status))
                .collect(Collectors.toSet());

        Set<DependencyDto> dependents = issue.dependents.stream()
                .map(d -> new DependencyDto(d.id, d.title, d.status))
                .collect(Collectors.toSet());

        IssueDto dto = IssueDto.fromIssue(issue);
        return Response.ok(dto).build();
    }

    /**
     * Add a dependency relationship.
     */
    @POST
    @Path("/{id}/dependencies")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addDependency(@PathParam("id") Long id, @Valid AddDependencyRequest request) {
        issueService.addDependency(id, request.dependencyId);
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    /**
     * Remove a dependency relationship.
     */
    @DELETE
    @Path("/{id}/dependencies/{dependencyId}")
    public Response removeDependency(@PathParam("id") Long id, @PathParam("dependencyId") Long dependencyId) {
        issueService.removeDependency(id, dependencyId);
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    /**
     * Get comments for an issue.
     */
    @GET
    @Path("/{id}/comments")
    public Response getComments(@PathParam("id") Long id) {
        List<Comment> comments = issueService.getComments(id);
        List<CommentDto> dtos = comments.stream()
                .map(CommentDto::fromComment)
                .collect(Collectors.toList());
        return Response.ok(new CommentsListDto(dtos)).build();
    }

    /**
     * Add a comment to an issue.
     */
    @POST
    @Path("/{id}/comments")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addComment(@PathParam("id") Long id, @Valid AddCommentRequest request) {
        Comment comment = issueService.addComment(id, request.author, request.body);
        return Response.status(Response.Status.CREATED).entity(CommentDto.fromComment(comment)).build();
    }
}
