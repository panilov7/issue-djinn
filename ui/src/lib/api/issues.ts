import { request } from './client';
import type { IssueDetail, IssueListParams, IssueListResponse, SortDirection } from '$lib/types';

const BASE = '/api/issues';

export function listIssues(params: IssueListParams = {}): Promise<IssueListResponse> {
	const qs = buildQueryString(params);
	return request<IssueListResponse>(qs ? `${BASE}?${qs}` : BASE);
}

/**
 * The detail shape. `childDirection` is the detail endpoint's only sort
 * parameter — its rows are the issue itself and its children, so parent sort
 * params are not its parameters at all (CONTEXT.md → `sort` / `direction` /
 * `child_direction` queries).
 */
export function getIssue(id: number, childDirection?: SortDirection): Promise<IssueDetail> {
	const qs = childDirection ? `?child_direction=${childDirection}` : '';
	return request<IssueDetail>(`${BASE}/${id}${qs}`);
}

/** The fields `POST /api/issues` accepts. The backend defaults `description` to empty. */
export interface CreateIssueInput {
	title: string;
	description?: string;
	parentId?: number | null;
	labels?: string[];
}

export function createIssue(input: CreateIssueInput): Promise<IssueDetail> {
	return request<IssueDetail>(BASE, {
		method: 'POST',
		body: JSON.stringify(input)
	});
}

export function updateIssue(
	id: number,
	patch: {
		title?: string;
		description?: string;
		assignee?: string | null;
		labels?: string[];
	}
): Promise<IssueDetail> {
	return request<IssueDetail>(`${BASE}/${id}`, {
		method: 'PATCH',
		body: JSON.stringify(patch)
	});
}

export function closeIssue(id: number, comment: string, author: string): Promise<IssueDetail> {
	return request<IssueDetail>(`${BASE}/${id}/close`, {
		method: 'POST',
		body: JSON.stringify({ comment, author })
	});
}

export function reopenIssue(id: number, comment: string, author: string): Promise<IssueDetail> {
	return request<IssueDetail>(`${BASE}/${id}/reopen`, {
		method: 'POST',
		body: JSON.stringify({ comment, author })
	});
}

export function unassignIssue(id: number): Promise<IssueDetail> {
	return request<IssueDetail>(`${BASE}/${id}/unassign`, { method: 'POST' });
}

function buildQueryString(params: IssueListParams): string {
	const search = new URLSearchParams();
	// 'all' means "no status restriction", which the API spells as omission.
	if (params.status && params.status !== 'all') search.set('status', params.status);
	if (params.parent !== undefined) search.set('parent', String(params.parent));
	if (params.flat) search.set('flat', 'true');
	for (const label of params.labels ?? []) search.append('label', label);
	if (params.assignee !== undefined) search.set('assignee', params.assignee);
	if (params.hasAssignee !== undefined) search.set('has_assignee', String(params.hasAssignee));
	if (params.hasOpenDependency !== undefined)
		search.set('has_open_dependency', String(params.hasOpenDependency));
	if (params.search) search.set('search', params.search);
	if (params.sort) search.set('sort', params.sort);
	if (params.direction) search.set('direction', params.direction);
	if (params.childDirection) search.set('child_direction', params.childDirection);
	if (params.offset !== undefined) search.set('offset', String(params.offset));
	if (params.limit !== undefined) search.set('limit', String(params.limit));
	return search.toString();
}
