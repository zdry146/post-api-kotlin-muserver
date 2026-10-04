# post-api frontend image (vanilla TS + Express)
# Strategy: ALL compilation happens on Jenkins agent (has Gradle/npm cache).
# This image is runtime-only: it copies pre-built `dist/`, `node_modules/`,
# `server.js`, and `package.json` from the build context into a slim base.
#
# Trade-off: this Dockerfile is no longer standalone-buildable. CI/CD
# always runs `npm ci` + `npm run build:fast` in the agent first, so
# `dist/` and `node_modules/` are guaranteed to exist when this builds.

FROM node:20-alpine
WORKDIR /app

# Copy pre-built artifacts from Jenkins agent's vite stage
COPY package.json package-lock.json ./
COPY node_modules ./node_modules
COPY server.js ./
COPY dist ./dist

# Express 端口
EXPOSE 5174

# BACKEND_URL 由 K8s 注入；默认指向 K8s 内 service
ENV PORT=5174 \
    BACKEND_URL="http://localhost:8090"

# healthcheck: Express serve static index.html 200
HEALTHCHECK --interval=10s --timeout=3s --retries=5 \
  CMD wget --quiet --tries=1 --spider http://localhost:5174/ || exit 1

CMD ["node", "server.js"]