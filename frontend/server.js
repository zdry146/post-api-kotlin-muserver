// post-api-frontend Express server
//
// Serves the built vanilla TS bundle (dist/) on port 5174, and proxies
// /api/* requests to the mu-server backend on :8080.
//
// Usage:
//   npm run build         # produces dist/
//   npm start             # runs this server
//   PORT=3000 npm start   # custom port
//   BACKEND_URL=http://host:8080 npm start  # custom backend
//
// Architecture (production-style):
//   Browser → Express (5174)
//            ├─ /api/* → mu-server (8080)
//            └─ /*     → static dist/ + SPA fallback to index.html

import express from 'express';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createProxyMiddleware } from 'http-proxy-middleware';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PORT = Number(process.env.PORT) || 5174;
const HOST = process.env.HOST || '0.0.0.0';
// mu-server (Kotlin) runs on :8090 because Jenkins squats :8080.
// Override via BACKEND_URL env var for staging/prod.
const BACKEND_URL = process.env.BACKEND_URL || 'http://127.0.0.1:8090';
const DIST_DIR = path.join(__dirname, 'dist');

const app = express();

// Shared proxy error handler (DRY for /api and /health routes).
function proxyErrorHandler(err, _req, res) {
  console.error('[proxy] error:', err.message);
  res.status(502).json({
    code: 502,
    message: `Backend unavailable: ${err.message}`,
    data: null,
  });
}

function proxyLogHandler(_proxyReq, req) {
  console.log(`[proxy] ${req.method} ${req.url} → ${BACKEND_URL}${req.url}`);
}

// Proxy /api/* → backend. Must come BEFORE static serving so requests
// to /api/* are forwarded rather than 404'd as missing static files.
//
// IMPORTANT: `app.use('/api', proxy)` strips the '/api' prefix before
// forwarding. We re-add it via pathRewrite so the backend receives
// the full path (mu-server registers handlers at /api/posts/*).
app.use(
  '/api',
  createProxyMiddleware({
    target: BACKEND_URL,
    changeOrigin: false,
    pathRewrite: { '^/': '/api/' },
    logLevel: process.env.NODE_ENV === 'production' ? 'warn' : 'info',
    onProxyReq: proxyLogHandler,
    onError: proxyErrorHandler,
  }),
);

// Proxy /health/* → backend (liveness + readiness probes).
// Special handling: /health exactly (no trailing path) must keep /health
// prefix after Express's app.use() strip. pathRewrite function handles
// both `/health` and `/health/ready` cases.
app.use(
  '/health',
  createProxyMiddleware({
    target: BACKEND_URL,
    changeOrigin: false,
    pathRewrite: (path) => (path === '/' || path === '' ? '/health' : `/health${path}`),
    logLevel: process.env.NODE_ENV === 'production' ? 'warn' : 'info',
    onProxyReq: proxyLogHandler,
    onError: proxyErrorHandler,
  }),
);

// Proxy /openapi.json → backend (API spec endpoint for tools/consumers).
// Use app.all() instead of app.use(path, ...) because http-proxy-middleware
// v3.0.x with an exact path via app.use is unreliable — app.all() with
// direct middleware invocation works consistently.
const openapiProxy = createProxyMiddleware({
  target: BACKEND_URL,
  changeOrigin: false,
  logLevel: process.env.NODE_ENV === 'production' ? 'warn' : 'info',
  onProxyReq: proxyLogHandler,
  onError: proxyErrorHandler,
});
app.all('/openapi.json', openapiProxy);

// Serve built static assets (JS, CSS, images).
app.use(express.static(DIST_DIR));

// SPA fallback: any GET that isn't /api serves index.html.
// Excludes /assets/* so missing assets 404 instead of silently
// returning index.html (which masks bugs).
app.get(/^\/(?!api\/|assets\/).*/, (_req, res) => {
  res.sendFile(path.join(DIST_DIR, 'index.html'), (err) => {
    if (err) {
      res.status(404).json({
        code: 404,
        message: 'index.html not found — did you run `npm run build`?',
        data: null,
      });
    }
  });
});

// 404 for unmatched /api paths (proxy doesn't catch them if backend is down).
app.use((_req, res) => {
  res.status(404).json({
    code: 404,
    message: 'Not found',
    data: null,
  });
});

const server = app.listen(PORT, HOST, () => {
  console.log(`post-api-frontend server listening on http://${HOST}:${PORT}`);
  console.log(`  /api/*  → ${BACKEND_URL}`);
  console.log(`  static  → ${DIST_DIR}`);
});

// Graceful shutdown — important for Playwright test cleanup.
const shutdown = (signal) => {
  console.log(`\n[${signal}] shutting down...`);
  server.close(() => process.exit(0));
  // Force exit after 5s if connections hang
  setTimeout(() => process.exit(1), 5000).unref();
};
process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));
