# syntax=docker/dockerfile:1.6

# ---------- Build stage ----------
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

# Pre-fetch dependencies for better layer caching
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    apt-get update && apt-get install -y --no-install-recommends maven \
    && mvn -B -q -DskipTests dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
    && cp target/vietsms-gateway-*.jar app.jar

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd --system vietsms && useradd --system --gid vietsms vietsms \
    && mkdir -p /app/data && chown -R vietsms:vietsms /app

COPY --from=build --chown=vietsms:vietsms /workspace/app.jar app.jar

USER vietsms
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"

HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
    CMD wget --spider -q http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
