// API client — fetch-based, with AbortController support (matches React's axios pattern).
// No axios, no zod: just typed fetch wrappers.

import type {
  ApiResult,
  PageResponse,
  Post,
  CreatePostRequest,
  UpdatePostRequest,
  ApiResultVoid,
} from '../types';
import { API_BASE_URL } from '../constants';

/**
 * Standard fetch wrapper — JSON in, JSON out, errors surfaced as ApiResult-style rejections.
 *
 * Returns ApiResult<T> for 2xx responses; throws for network/parse/5xx errors.
 */
async function request<T>(
  method: string,
  path: string,
  body?: unknown,
  params?: Record<string, string | number | undefined>,
  signal?: AbortSignal,
): Promise<ApiResult<T>> {
  const url = new URL(API_BASE_URL + path, window.location.origin);
  if (params) {
    for (const [k, v] of Object.entries(params)) {
      if (v !== undefined) url.searchParams.set(k, String(v));
    }
  }

  const init: RequestInit = {
    method,
    headers: { 'Content-Type': 'application/json' },
    signal,
    // Bypass browser HTTP cache: backend sets Cache-Control: max-age=60 on
    // list endpoints (omo P2-8). Without this, post-mutation refetches
    // would return stale data. See: post-crud test 3 (编辑帖子) was failing
    // because GET /all returned cached old titles.
    cache: 'no-store',
  };
  if (body !== undefined) {
    init.body = JSON.stringify(body);
  }

  const res = await fetch(url.toString(), init);

  // Backend always returns JSON ApiResult shape (even for errors with custom codes)
  let parsed: ApiResult<T>;
  try {
    parsed = (await res.json()) as ApiResult<T>;
  } catch (err) {
    throw new NetworkError(`Invalid JSON response (HTTP ${res.status})`, res.status);
  }

  if (!res.ok && (res.status < 200 || res.status >= 300)) {
    // Server returned non-2xx; parsed may still have code=200 for soft-errors,
    // but if HTTP itself failed, surface as NetworkError.
    throw new NetworkError(
      `HTTP ${res.status}: ${parsed.message ?? res.statusText}`,
      res.status,
    );
  }

  return parsed;
}

/** Custom error class for network/HTTP failures (distinct from logical API failures). */
export class NetworkError extends Error {
  constructor(message: string, public status: number) {
    super(message);
    this.name = 'NetworkError';
  }
}

/**
 * isSuccess — mirrors the React app's helper. ApiResult.code 200 (or 0) means OK.
 * Kept as a separate export so consumers don't have to know the magic number.
 */
export function isSuccess<T>(result: ApiResult<T> | ApiResultVoid): boolean {
  return result.code === 200 || result.code === 0;
}

export const postApi = {
  listPublished: (page = 0, size = 10, signal?: AbortSignal) =>
    request<PageResponse<Post>>('GET', '/published', undefined, { page, size }, signal),

  listAll: (page = 0, size = 10, signal?: AbortSignal) =>
    request<PageResponse<Post>>('GET', '/all', undefined, { page, size }, signal),

  getById: (id: number, signal?: AbortSignal) =>
    request<Post>('GET', `/${id}`, undefined, undefined, signal),

  create: (data: CreatePostRequest, signal?: AbortSignal) =>
    request<Post>('POST', '', data, undefined, signal),

  update: (id: number, data: UpdatePostRequest, signal?: AbortSignal) =>
    request<Post>('PUT', `/${id}`, data, undefined, signal),

  delete: (id: number, signal?: AbortSignal) =>
    request<null>('DELETE', `/${id}`, undefined, undefined, signal),

  search: (keyword: string, page = 0, size = 10, signal?: AbortSignal) =>
    request<PageResponse<Post>>('GET', '/search', undefined, { keyword, page, size }, signal),

  togglePublish: (id: number, signal?: AbortSignal) =>
    request<Post>('POST', `/${id}/toggle-publish`, undefined, undefined, signal),

  like: (id: number, signal?: AbortSignal) =>
    request<Post>('POST', `/${id}/like`, undefined, undefined, signal),

  // Unlike endpoint: in omo's backend enhancement plan (P1, item 2).
  // Frontend exposes this regardless of backend availability — failure shows as alert.
  unlike: (id: number, signal?: AbortSignal) =>
    request<Post>('POST', `/${id}/unlike`, undefined, undefined, signal),
};
