# ── Stage 1: Build uber-JAR ────────────────────────────────────────────────────
FROM gradle:8.11-jdk21 AS build

WORKDIR /app

COPY . .

# Fake Android SDK to pass composeApp/shared Android plugin configuration
ENV ANDROID_HOME=/tmp/android-sdk
RUN mkdir -p /tmp/android-sdk && \
    echo "sdk.dir=/tmp/android-sdk" >> local.properties

# SERVER_URL is baked into the JAR via src/jvmMain/resources/api_base_url.txt
ARG SERVER_URL=http://localhost:8081
RUN mkdir -p composeApp/src/jvmMain/resources && \
    printf '%s' "$SERVER_URL" > composeApp/src/jvmMain/resources/api_base_url.txt

RUN chmod +x gradlew && \
    ./gradlew :composeApp:packageUberJarForCurrentOS --no-daemon

RUN find composeApp/build/compose/jars/ -name "*.jar" \
        -exec cp {} /tmp/taskmanager.jar \;

# ── Stage 2: nginx download server ─────────────────────────────────────────────
FROM nginx:1.27-alpine

COPY --from=build /tmp/taskmanager.jar /usr/share/nginx/html/taskmanager.jar
COPY docker/desktop/nginx.conf /etc/nginx/conf.d/default.conf
COPY docker/desktop/entrypoint.sh /app/entrypoint.sh
COPY docker/desktop/index.html /usr/share/nginx/html/index.html
RUN chmod +x /app/entrypoint.sh

EXPOSE 8080
ENTRYPOINT ["/app/entrypoint.sh"]
