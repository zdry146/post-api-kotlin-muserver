import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright E2E config for the post-api vanilla TS frontend.
 *
 * Run modes:
 *   - Manual: `npm start` (in another shell), then `npm run test:e2e`
 *   - Auto:   `npm run test:e2e` (webServer boots both backend + frontend)
 *
 * Targets:
 *   - Backend (mu-server) on :8080  — assumed running externally
 *   - Frontend (Express) on :5174   — spawned by webServer
 */
export default defineConfig({
  testDir: './e2e',
  // E2E tests touch shared state (backend DB) — run serially.
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [
    ['list'],
    ['html', { outputFolder: 'test-results/report', open: 'never' }],
  ],
  timeout: 30_000,
  expect: { timeout: 5_000 },

  use: {
    // In CI (Jenkins), tests target the K8s-deployed app via PLAYWRIGHT_BASE_URL
    // (e.g. http://192.168.232.128:30080 = NodePort). Locally, default to
    // http://127.0.0.1:5174 when running the dev express server.
    baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:5174',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },

  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        // Disable webkit/firefox for speed — only chromium needed for CI smoke.
        // Add more projects if cross-browser coverage is needed.
      },
    },
  ],

  // In CI (Jenkins), the app is already deployed to K8s — Playwright should
  // NOT spawn its own webServer (port 5174 may collide with other processes).
  // Local dev still benefits from the auto-spawn behavior.
  ...(process.env.CI
    ? {}
    : {
        webServer: [
          {
            command: 'npm run build:fast && node server.js',
            port: 5174,
            reuseExistingServer: true,
            timeout: 60_000,
            stdout: 'pipe' as const,
            stderr: 'pipe' as const,
          },
        ],
      }),
});
