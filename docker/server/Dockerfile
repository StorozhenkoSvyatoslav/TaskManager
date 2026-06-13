# ── Stage 1: Build fat JAR ─────────────────────────────────────────────────────
FROM gradle:8.11-jdk21 AS build

WORKDIR /app

# Copy everything needed for the build
COPY . .

# Fake Android SDK so the shared module's androidLibrary plugin doesn't fail
# during Gradle configuration (we only compile JVM targets)
ENV ANDROID_HOME=/tmp/android-sdk
RUN mkdir -p /tmp/android-sdk && \
    echo "sdk.dir=/tmp/android-sdk" >> local.properties

# Use settings.server.gradle.kts to skip composeApp (no Android/Compose tooling needed)
RUN chmod +x gradlew && \
    ./gradlew -c settings.server.gradle.kts :server:buildFatJar --no-daemon

# ── Stage 2: Runtime image ─────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app
COPY --from=build /app/server/build/libs/*-all.jar app.jar

# PORT is injected by Railway; defaults to 8081 locally
ENV PORT=8081
EXPOSE $PORT

CMD ["java", "-jar", "app.jar"]
