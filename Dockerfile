# post-api-kotlin-muserver backend image
# Strategy: gradle build runs ONCE on the Jenkins agent (faster — has local
# Gradle cache, faster Maven mirror). This image is runtime-only: it copies
# the pre-built jar from `$WORKSPACE/build/libs/` into a slim JRE base.
#
# Trade-off: this Dockerfile is no longer standalone-buildable (requires
# a pre-built jar in context). But CI/CD always builds the jar in the agent
# first, so this matches our pipeline.

FROM eclipse-temurin:21
WORKDIR /app

# Copy pre-built jar from Jenkins agent's gradle stage
COPY build/libs/*.jar app.jar

# mu-server 默认端口
EXPOSE 8090

# 环境变量默认值（生产由 K8s manifest 覆盖）
ENV JAVA_OPTS="-Xms256m -Xmx512m" \
    DB_URL="jdbc:postgresql://localhost:5432/testdb" \
    DB_USER="postgres" \
    DB_PASSWORD="postgres"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
