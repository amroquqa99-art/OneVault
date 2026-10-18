# Docker-first development

The project follows **Docker-first** principles. Android apps run on phones, not
in Docker. Docker isolates the toolchain (JDK, Gradle, Android SDK) for building,
testing, and packaging the APK. The host needs only Docker Engine/Desktop and
Docker Compose v2; no host-installed JDK, Android SDK, Gradle or adb is required
for builds.

## Commands

```bash
bash tools/docker-build.sh build  # build app-debug.apk
bash tools/docker-build.sh test   # Android JVM unit tests
bash tools/docker-build.sh check  # Android lint and unit tests
bash tools/docker-build.sh shell  # image shell for troubleshooting
```

APK path: `app/build/outputs/apk/debug/app-debug.apk`.

The first build **requires network access**, can download gigabytes of base
images and dependencies, and caches Gradle dependencies in a named Docker
volume. A working network is necessary to connect to Google/TeraBox from the
installed Android app too. Docker avoids host dependency collisions, but does
not eliminate network, host CPU architecture or Docker installation needs.

## Configuration

The debug build works with an empty `onevault.googleClientId` for offline
account-registry features, but live Google OAuth will be disabled. For a real
Google connection, create `local.properties` from the example; do not commit it.
Create an Android OAuth client registered for package `org.onevault.app`, and
ensure the redirect URI/scheme is supported for that client by Google.
Android app signing certificate SHA-1 must match the client configuration.

**Important:** Android debug signing key is generated per machine/build runner.
A GitHub Actions debug APK and local Docker debug APK will not have the same
signing certificate. Do not use debug builds for long-term vault storage;
create a secure stable signing procedure for a release APK, keep its signing
key out of the repo, and test recovery before upgrading.

`local.properties` is mounted into the build container but **never copied into
Docker images** or uploaded as a GitHub artifact. However, client IDs are
public configuration and embedded in the binary if supplied.

## Constraints

The Docker Compose `android` service is a **build job**, not an always-on cloud
service. A 24/7 share-gateway would be a separate Docker Compose service when
implemented. No such gateway exists yet. The portable chunking engine is
not connected to Google storage-limit bypass flows.
