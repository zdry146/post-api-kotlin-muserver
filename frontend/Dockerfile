# post-api frontend image (vanilla TS + Express)
# node:20-slim 是最小 Node 运行时（~50 MB base）
# build 时跑 `npm run build:fast`（已存在 dist/ 时 5ms）

FROM node:20-slim
WORKDIR /app

# 先装依赖以利用 Docker cache
COPY package.json package-lock.json ./
RUN npm ci --omit=dev

# 拷源码 + 构建
COPY . .
RUN npm run build:fast

# Express 端口
EXPOSE 5174

# BACKEND_URL 由 K8s 注入；默认指向 K8s 内 service
ENV PORT=5174 \
    BACKEND_URL="http://localhost:8090"

# healthcheck: Express serve static index.html 200
HEALTHCHECK --interval=10s --timeout=3s --retries=5 \
  CMD wget --quiet --tries=1 --spider http://localhost:5174/ || exit 1

CMD ["node", "server.js"]
