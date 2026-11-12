# syntax=docker/dockerfile:1
# Builds and runs only the Ktor sync server (module :server).

# ---- Build stage ----------------------------------------------------------
FROM eclipse-temurin:17-jdk AS build

# The Gradle build configures the Android modules too, so an Android SDK
# location must exist even though only the server is built.
ENV ANDROID_HOME=/opt/android-sdk
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl unzip \
 && rm -rf /var/lib/apt/lists/* \
 && mkdir -p "$ANDROID_HOME/cmdline-tools" \
 && curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip -o /tmp/cmdline-tools.zip \
 && unzip -q /tmp/cmdline-tools.zip -d "$ANDROID_HOME/cmdline-tools" \
 && mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest" \
 && rm /tmp/cmdline-tools.zip \
 && yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null \
 && "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platforms;android-36" > /dev/null

WORKDIR /src
COPY . .
RUN chmod +x gradlew && ./gradlew :server:buildFatJar --no-daemon --stacktrace

# ---- Runtime stage --------------------------------------------------------
FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /src/server/build/libs/activity-camera-server.jar /app/server.jar

# Bind-mount the NAS folder here (Dokploy: Advanced → Mounts).
ENV PORT=8080 \
    STORAGE_ROOT=/data
VOLUME ["/data"]
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/server.jar"]
