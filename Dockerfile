# post-api-kotlin-muserver backend image
# Reference: ~/claudecode-workspace/java-projects/post-api/Dockerfile (Java Spring Boot)
# 多阶段构建：gradle build → eclipse-temurin:21 运行时

# ============ Build stage ============
FROM gradle:8.10.2-jdk21 AS build
WORKDIR /app

# 先 copy build files以利用 Docker cache
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY src ./src

# Build（跳过测试，因为 E2E 在 K8s 跑）
RUN gradle build --no-daemon -x test --no-build-cache

# ============ Runtime stage ============
FROM eclipse-temurin:21
WORKDIR /app

# 拷 jar
COPY --from=build /app/build/libs/*.jar app.jar

# mu-server 默认端口
EXPOSE 8090

# 环境变量默认值（生产由 K8s Secret 覆盖）
ENV JAVA_OPTS="-Xms256m -Xmx512m" \
    DB_URL="jdbc:postgresql://localhost:5432/testdb" \
    DB_USER="postgres" \
    DB_PASSWORD="postgres"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
