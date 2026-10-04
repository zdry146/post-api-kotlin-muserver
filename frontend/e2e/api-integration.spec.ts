/**
 * API integration E2E — direct backend reachability via /api/* proxy.
 *
 * These tests don't render the SPA; they hit the Express proxy directly.
 * Useful for catching regressions in proxy setup vs UI tests.
 */

import { test, expect } from '@playwright/test';

test.describe('API 集成', () => {
  test('GET /health → 200 ok', async ({ request }) => {
    // Health endpoint may not exist on backend yet (it's in omo's P1-3 task).
    // If 404, we just log it — test still passes (proxy works).
    const res = await request.get('/health');
    expect([200, 404]).toContain(res.status());
    if (res.status() === 200) {
      const body = await res.json();
      expect(body).toHaveProperty('status');
    }
  });

  test('GET /api/posts/published → 200 with ApiResult shape', async ({ request }) => {
    const res = await request.get('/api/posts/published?page=0&size=10');
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body).toHaveProperty('code');
    expect(body).toHaveProperty('message');
    expect(body).toHaveProperty('data');
    // data should be a PageResponse
    if (body.code === 200 || body.code === 0) {
      expect(body.data).toHaveProperty('content');
      expect(Array.isArray(body.data.content)).toBe(true);
      expect(body.data).toHaveProperty('totalElements');
    }
  });

  test('GET /openapi.json → 200 (after omo P1-1)', async ({ request }) => {
    // This endpoint is added by omo's P1-1 backend task.
    // Pre-omo: 404. Post-omo: 200 with OpenAPI JSON.
    const res = await request.get('/openapi.json');
    expect([200, 404]).toContain(res.status());
    if (res.status() === 200) {
      const body = await res.json();
      expect(body).toHaveProperty('openapi');
      expect(body.openapi).toMatch(/^3\./);
    }
  });

  test('GET /api/posts/{nonexistent-id} → 4xx (proper error handling)', async ({ request }) => {
    const res = await request.get('/api/posts/999999999');
    expect(res.status()).toBeGreaterThanOrEqual(400);
    const body = await res.json();
    expect(body).toHaveProperty('code');
  });
});
