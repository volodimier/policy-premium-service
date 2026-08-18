# syntax=docker/dockerfile:1

# Build ------------------------------------------------------------------------
# Wrapper and build scripts are copied before the sources so that a source-only
# change does not invalidate the cached Gradle distribution download.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

COPY gradlew ./
COPY gradle gradle
RUN ./gradlew --version > /dev/null

COPY settings.gradle.kts build.gradle.kts ./
COPY config config
COPY src src

# Tests are not run on the default path. CI runs the full `check` task against every
# change before an image is built, so repeating them here would double build time to
# re-prove what the pipeline already proved - and a source change would re-run them on
# every rebuild.
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon

# Verify -----------------------------------------------------------------------
# Optional stage for anyone who wants to build and verify in one command without a
# working local Gradle:
#
#     docker build --target test .
#
# Nothing depends on this stage, so BuildKit skips it entirely during a normal build -
# it costs the default path nothing.
FROM build AS test
RUN --mount=type=cache,target=/root/.gradle ./gradlew check --no-daemon

# Extract ----------------------------------------------------------------------
# Splitting the fat jar into layers means a code change reuses the cached dependency
# layer instead of pushing ~23MB of unchanged libraries again.
FROM eclipse-temurin:21-jre-alpine AS extract
WORKDIR /extract
COPY --from=build /src/build/libs/*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# Runtime ----------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

# Runs as an unprivileged user. Application files stay owned by root, so the process
# can read its own code but not modify it.
RUN addgroup -S app && adduser -S -G app app

WORKDIR /application
COPY --from=extract /extract/extracted/dependencies/ ./
COPY --from=extract /extract/extracted/spring-boot-loader/ ./
COPY --from=extract /extract/extracted/snapshot-dependencies/ ./
COPY --from=extract /extract/extracted/application/ ./

USER app
EXPOSE 8080

# start-period covers JVM and context startup, so a slow boot is not reported as
# unhealthy. Uses busybox wget; alpine has no curl.
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

# MaxRAMPercentage rather than a fixed -Xmx, so the heap follows whatever memory limit
# the container is given instead of being wrong on every host but one.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "application.jar"]
