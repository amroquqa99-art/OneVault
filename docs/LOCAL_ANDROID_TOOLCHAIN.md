# Local Android toolchain (without Docker)

The preferred and CI build stays **Docker-first** (`tools/docker-build.sh all`).
This optional toolchain is an escape hatch for machines where Docker cannot run.
It never commits SDK archives, downloaded dependencies, APKs, or local secrets.

## Install

Requires Linux x86_64, JDK 17+ (JDK 21 supported), `curl`, `unzip`, and
`sha256sum`. Internet/DNS is necessary for downloading the vendor distributions
and Gradle/Maven dependencies. The installer verifies the published SHA-256
of official Google command-line tools and the official Gradle distribution.

```bash
bash tools/setup-android-toolchain.sh --check
bash tools/setup-android-toolchain.sh --install
bash tools/build-local.sh all
```

The installer requests interactive acceptance of Google's Android SDK licenses;
it will not accept license terms on the user's behalf. The SDK and Gradle live
in `~/.local/share/onevault-toolchain`, configurable via `ONEVAULT_TOOLCHAIN_HOME`.
The Android 35 platform, build tools 35.0.0 and platform-tools are installed.
No emulator is necessary to compile; a real-device test still requires a device
and Android debugging tooling.

Built and validated artifact (only **after a successful build**):
`app/build/outputs/apk/debug/app-debug.apk` + `.sha256`.

## What is not isolated?

The JDK needs to be installed on the host for this fallback. For truly isolated
builds use the pinned Dockerfile and Compose setup as documented in
[DOCKER.md](DOCKER.md).

## Diagnostics in ChatGPT build environment — 2026-10-18

- `java -version`: OpenJDK 21 exists.
- `kotlinc`: available; portable core tests pass.
- `docker`, `gradle`, `sdkmanager`, Android 35 platform: not installed.
- Official Android SDK archive URL is unreachable (`curl: (6) Could not resolve host: dl.google.com`).
- The Vercel sandbox alternative returned HTTP 403 (scope authorization), and
  private GitHub Actions jobs fail before a runner is assigned.
- Therefore **no APK has been compiled or validated here**; source checks are not
  APK tests. The container cannot install the necessary packages without a
  permitted download path.
