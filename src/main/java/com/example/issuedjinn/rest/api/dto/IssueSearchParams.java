package com.example.issuedjinn.rest.api.dto;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;

import java.util.List;

/**
 * Query parameters for listing issues.
 * Used with @BeanParam to bind query parameters from the REST API.
 */
public class IssueSearchParams {

    @QueryParam("status")
    public String status;

    @QueryParam("parent")
    public Long parent;

    /** True lists every matching issue as its own row, with nothing grouped under it. */
    @QueryParam("flat")
    public Boolean flat;

    @QueryParam("label")
    public List<String> labels;

    @QueryParam("assignee")
    public String assignee;

    @QueryParam("has_assignee")
    public Boolean hasAssignee;

    @QueryParam("has_open_dependency")
    public Boolean hasOpenDependency;

    @QueryParam("search")
    public String search;

    /**
     * The field parent-level rows are ordered by — one of
     * {@code createdAt|updatedAt|title|id}. Children take
     * {@link #childDirection} instead; a {@code ?parent=N} listing ignores
     * this parameter and {@link #direction} entirely.
     */
    @QueryParam("sort")
    public String sort;

    /** The direction parent-level rows run in — {@code asc|desc}. */
    @QueryParam("direction")
    public String direction;

    /** The direction children are served in, wherever children appear — {@code asc|desc}. */
    @QueryParam("child_direction")
    public String childDirection;

    @QueryParam("offset")
    @DefaultValue("0")
    public Integer offset;

    @QueryParam("limit")
    @DefaultValue("50")
    public Integer limit;
}
