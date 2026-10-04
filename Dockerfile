# post-api-kotlin-muserver backend image
# Strategy: gradle installDist runs on Jenkins agent (has Gradle/Maven cache).
# That produces build/install/post-api-kotlin-muserver/ with bin/ + lib/.
# This image just copies that directory and runs the bin/ script.
#
# Why installDist instead of jar:
#   - The plain `gradle build` task produces build/libs/*.jar (sources jar, no
#     Main-Class manifest). Docker would fail with "no main manifest attribute".
#   - `gradle installDist` (application plugin) creates bin/<app> shell script
#     that wires the classpath from lib/*.jar + invokes the main class.
#
# Trade-off: image requires pre-built installDir in build context.

FROM eclipse-temurin:21
WORKDIR /app

# Copy pre-built install dir (bin/ + lib/ + conf/) from gradle installDist stage
COPY build/install/post-api-kotlin-muserver/ /app/

# mu-server 默认端口
EXPOSE 8090

# 环境变量默认值（生产由 K8s manifest 覆盖）
ENV JAVA_OPTS="-Xms256m -Xmx512m" \
    DB_URL="jdbc:postgresql://localhost:5432/testdb" \
    DB_USER="postgres" \
    DB_PASSWORD="***"

# Run the bin/ script — it sets up classpath from lib/*.jar and invokes
# com.example.postapi.Application (configured in build.gradle.kts)
ENTRYPOINT ["./bin/post-api-kotlin-muserver"]