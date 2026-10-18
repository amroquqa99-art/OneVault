# Reproducible ANDROID BUILD environment. This container BUILDS APKs; the APK
# runs on Android itself, not inside Docker. Pin SDK and Gradle versions.
FROM gradle:8.11.1-jdk17 AS gradle-dist
FROM ghcr.io/cirruslabs/android-sdk:35
COPY --from=gradle-dist /opt/gradle /opt/gradle
ENV PATH="/opt/gradle/bin:${PATH}" \
    GRADLE_USER_HOME=/root/.gradle \
    JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8"
WORKDIR /workspace
# The project is mounted by Compose. Do not bake credentials or source into image.
CMD ["gradle", "--no-daemon", ":app:assembleDebug"]
