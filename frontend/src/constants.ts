// Constants — kept as a separate file so changing them later is one edit.

export const PAGE_SIZE = 10;
export const DEFAULT_PAGE = 0;
export const CONTENT_PREVIEW_LENGTH = 100;

// Backend base URL. In dev, Vite proxies /api/* to localhost:8080 (see vite.config.ts),
// so we use a relative path here. In production build, the static files are served
// by the mu-server backend itself and /api/* resolves correctly.
export const API_BASE_URL = '/api/posts';
