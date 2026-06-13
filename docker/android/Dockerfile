# ── Stage 1: Build APK ─────────────────────────────────────────────────────────
FROM gradle:8.11-jdk21 AS build

USER root

WORKDIR /app

# Install Android SDK command-line tools
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

RUN apt-get update && apt-get install -y wget unzip && rm -rf /var/lib/apt/lists/*

RUN mkdir -p $ANDROID_HOME/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
         -O /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d $ANDROID_HOME/cmdline-tools && \
    mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

RUN yes | sdkmanager --licenses 2>/dev/null || true && \
    sdkmanager "platforms;android-36" "build-tools;36.0.0"

COPY . .

# SERVER_URL is baked into BuildConfig.SERVER_URL at compile time
ARG SERVER_URL=http://10.0.2.2:8081
RUN chmod +x gradlew && \
    ./gradlew :composeApp:assembleDebug --no-daemon -PSERVER_URL="$SERVER_URL"

RUN find composeApp/build/outputs/apk/debug/ -name "*.apk" \
        -exec cp {} /tmp/taskmanager.apk \;

# ── Stage 2: nginx download server ─────────────────────────────────────────────
FROM nginx:1.27-alpine

COPY --from=build /tmp/taskmanager.apk /usr/share/nginx/html/taskmanager.apk
COPY docker/android/nginx.conf /etc/nginx/conf.d/default.conf
COPY docker/android/entrypoint.sh /app/entrypoint.sh
COPY docker/android/index.html /usr/share/nginx/html/index.html
RUN chmod +x /app/entrypoint.sh

EXPOSE 8080
ENTRYPOINT ["/app/entrypoint.sh"]
