// Base fetch wrapper shared by every per-resource API client module.
// Throws an ApiError (status + machine-readable code) instead of returning
// a Response, so callers get a typed error surface from every client call.

export class ApiError extends Error {
	constructor(
		readonly status: number,
		readonly code: string,
		message: string
	) {
		super(message);
		this.name = 'ApiError';
	}
}

interface ErrorEnvelope {
	error?: { code?: string; message?: string };
}

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
	const response = await fetch(path, {
		...init,
		headers: {
			'content-type': 'application/json',
			...(init.headers ?? {})
		}
	});

	if (!response.ok) throw await toApiError(response);

	if (response.status === 204) return undefined as T;
	return response.json() as Promise<T>;
}

async function toApiError(response: Response): Promise<ApiError> {
	let code = `HTTP_${response.status}`;
	let message = `Request failed with status ${response.status}`;
	try {
		const body = (await response.json()) as ErrorEnvelope;
		if (body.error?.code) code = body.error.code;
		if (body.error?.message) message = body.error.message;
	} catch {
		// Non-JSON error body — keep the default message.
	}
	return new ApiError(response.status, code, message);
}

/** Narrow a fetch error into a friendly message for toasts / alerts. */
export function apiErrorMessage(error: unknown): string {
	if (error instanceof ApiError) return error.message;
	if (error instanceof Error) return error.message;
	return 'Unexpected error';
}
