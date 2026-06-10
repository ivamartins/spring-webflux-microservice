# syntax=docker/dockerfile:1.6

# --- Build ---
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build

# Cache dependencies
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp dependency:go-offline

COPY src src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp package -DskipTests

# --- Runtime ---
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Non-root user
RUN useradd -ms /bin/bash spring
USER spring

COPY --from=build /build/target/spring-webflux-microservice-*.jar /app/app.jar

# JVM / GC tuning
ENV JAVA_OPTS="-XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:+UseStringDeduplication \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/tmp/heap.hprof \
    -Xlog:gc*,gc+heap=info:file=/tmp/gc.log:time,uptime,level,tags:filecount=5,filesize=50m"

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
  CMD wget -q -O - http://localhost:8080/actuator/health/liveness | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
