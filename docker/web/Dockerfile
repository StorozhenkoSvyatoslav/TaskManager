# ── Stage 1: Build WasmJS production bundle ────────────────────────────────────
FROM gradle:8.11-jdk21 AS build

WORKDIR /app

COPY . .

# Fake Android SDK — the composeApp and shared modules apply the Android plugin
# during Gradle configuration even for non-Android builds. An existing ANDROID_HOME
# directory is enough to pass the configuration phase without running Android tasks.
ENV ANDROID_HOME=/tmp/android-sdk
RUN mkdir -p /tmp/android-sdk && \
    echo "sdk.dir=/tmp/android-sdk" >> local.properties

# Node.js 25 (downloaded by Kotlin WasmJS toolchain) requires libatomic
RUN apt-get update && apt-get install -y libatomic1 && rm -rf /var/lib/apt/lists/*

RUN chmod +x gradlew && \
    ./gradlew :composeApp:wasmJsBrowserDistribution --no-daemon

# Inject <script src="/config.js"> into the generated index.html so the app
# can read window.__API_BASE__ (set at container startup by entrypoint.sh)
RUN sed -i 's|<head>|<head><script src="/config.js"></script>|' \
        composeApp/build/dist/wasmJs/productionExecutable/index.html

# ── Stage 2: nginx static server ───────────────────────────────────────────────
FROM nginx:1.27-alpine

COPY --from=build /app/composeApp/build/dist/wasmJs/productionExecutable /usr/share/nginx/html
COPY docker/web/nginx.conf /etc/nginx/conf.d/default.conf
COPY docker/web/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# PORT is injected by Railway; entrypoint.sh substitutes it into nginx.conf
EXPOSE 8080

ENTRYPOINT ["/app/entrypoint.sh"]
