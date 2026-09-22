import { request } from './client';
import type { Comment, CommentsListResponse } from '$lib/types';

const BASE = '/api/issues';

export function listComments(issueId: number): Promise<CommentsListResponse> {
	return request<CommentsListResponse>(`${BASE}/${issueId}/comments`);
}

export function addComment(issueId: number, author: string, body: string): Promise<Comment> {
	return request<Comment>(`${BASE}/${issueId}/comments`, {
		method: 'POST',
		body: JSON.stringify({ author, body })
	});
}
