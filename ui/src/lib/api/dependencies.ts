import { request } from './client';

const BASE = '/api/issues';

export function addDependency(issueId: number, dependencyId: number): Promise<void> {
	// Backend expects snake_case `dependency_id` in the body.
	return request<void>(`${BASE}/${issueId}/dependencies`, {
		method: 'POST',
		body: JSON.stringify({ dependency_id: dependencyId })
	});
}

export function removeDependency(issueId: number, dependencyId: number): Promise<void> {
	return request<void>(`${BASE}/${issueId}/dependencies/${dependencyId}`, {
		method: 'DELETE'
	});
}
