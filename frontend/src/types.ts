// Type definitions mirroring the OpenAPI spec + Java/Kotlin DTOs.
// See: openapi-tests/openapi-spec.json for canonical schema.

export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface Post {
  id: number;
  title: string;
  content: string;
  authorName: string;
  coverImage: string | null;
  viewCount: number;
  likeCount: number;
  isPublished: boolean;
  isDeleted: boolean;
  createdAt: string;  // ISO 8601 from backend
  updatedAt: string;
}

export interface CreatePostRequest {
  title: string;
  content: string;
  authorName: string;
  coverImage?: string;
}

export interface UpdatePostRequest {
  title: string;
  content: string;
  authorName?: string;
  coverImage?: string;
  isPublished?: boolean;
}

// Convenience: ApiResult<void> shape (used for delete responses)
export type ApiResultVoid = ApiResult<null>;
