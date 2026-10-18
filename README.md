# OneVault — Personal Cloud & Identity Manager

> **Status: v0.2.3-alpha SOURCE PROJECT, NOT a finished production APK.**
> Personal, local-first Android app. The source is intended for a private GitHub repository.

**OneVault** brings a personal account registry, platform-registration inventory, optional encrypted password vault, and multiple Google Drive file indexes into one interface. The long-term roadmap includes compliant storage adapters, TeraBox, split-file recovery and optional unified web sharing.

## Implemented in source

- Android UI: dashboard, account registry, per-account platform-category breakdown and confidence counts, platform directory, cached cloud file explorer, vault, settings.
- Local SQLite account and platform graph, categories, states, linked-evidence confidence, per-account storage reserve.
- Manual account/platform/link management and CSV account import (`email,alias,category`).
- Offline email-header evidence import from **user-exported MBOX/EML files**. Marks results as **probable**, never confirmed; only reads message headers, not message bodies.
- AES-GCM secret encryption using Android Keystore; password reveal + delete.
- Password-protected portable backup and restore (PBKDF2-HMAC-SHA256 + AES-256-GCM). Google OAuth refresh tokens are deliberately excluded from backups.
- Google OAuth via AppAuth (requires your own Google Cloud Android OAuth client configuration); access/refresh state encrypted with Keystore.
- Google Drive API: about/storage quota, paginated file list and cache, native Drive links, downloads for binary files, resumable chunked uploads of files chosen in Android's file picker.
- WorkManager persistent upload jobs with network-required scheduling, exponential retry, session checkpoints and account-specific reserve policy.
- Streamed local chunk split/reassemble engine with SHA-256 verification (for approved/user-controlled storage targets; **not wired to bypass Google storage limits**).
- GitHub Actions workflow to compile debug APK when pushed to a repository.

## Not yet implemented or validated

- **No APK is supplied**, because this environment does not have Android SDK/Gradle and no Android app build has been executed.
- The Android code has **not been compiled or device-tested**. Only pure Kotlin core tests have been executed.
- The public Google client ID and redirect registration must be configured by the owner. No OAuth client is distributed.
- No direct live Gmail discovery: requires separate OAuth restricted permission and review. Offline exported-mail evidence only.
- No TeraBox client: requires official developer access, supported API and agreement.
- No automated storage splitting across Google accounts; no browser-friendly unified reassembly/link gateway.
- No automatic merge/sync conflicts, no native Google Workspace export, no password autofill service, no polished UX/accessibility review.
- All Google Drive accounts keep independent quotas. Displaying their totals does **not** create a Google storage pool.
- This is **alpha**, and credentials/critical files should not rely on it without audit and external backups.

## Recommended: build the Android APK with Docker Compose

**Host prerequisite: Docker Engine/Desktop + Docker Compose v2 only.** JDK,
Android SDK and Gradle live inside the versioned container image. This builds
an Android APK (the mobile app itself does not run in Docker).

```bash
bash tools/docker-build.sh build    # app/build/outputs/apk/debug/app-debug.apk
bash tools/docker-build.sh test     # JVM unit tests
bash tools/docker-build.sh check    # lint and unit tests
```

This is also the exact build system used in `.github/workflows/android.yml`.
First run needs internet to download Docker images/Gradle dependencies.
See [Docker build instructions](docs/DOCKER.md). **APK build has not yet
been validated here** because Docker Engine is unavailable in this runtime.

## Alternate: build with Android Studio

1. Install **Android Studio**, Android SDK Platform 35, Build Tools 35, JDK 17 and Gradle 8.11.1 (or generate Gradle wrapper locally).
2. Open this repository as a Gradle project in Android Studio.
3. In Google Cloud Console, enable **Google Drive API**, configure OAuth consent for personal use, and create an Android OAuth client for package `org.onevault.app` and your signing key's SHA-1.
4. Register an OAuth redirect configuration supported for this Android client. The `onevault.redirectScheme` parameter in `local.properties` must match it. *Google may impose additional OAuth redirect restrictions and the actual flow must be verified on device.*
5. Copy `local.properties.example` to `local.properties`, add a local Android SDK path and your OAuth client ID and redirect scheme:

   ```properties
   sdk.dir=/home/YOUR_USER/Android/Sdk
   onevault.googleClientId=YOUR_OAUTH_CLIENT_ID.apps.googleusercontent.com
   onevault.redirectScheme=YOUR_REGISTERED_SCHEME
   ```

6. In Android Studio choose **Build > Build APK(s)**. Or, with a compatible Gradle installation, run:

   ```sh
   bash tools/docker-build.sh build
   ```

7. Android debug build output (if compilation succeeds): `app/build/outputs/apk/debug/app-debug.apk`.
8. Connect one Google account through the Accounts screen and verify OAuth/Drive permission flow before adding others.

### Important OAuth scope warning

This source requests `openid`, `email`, `profile`, `drive.readonly` and `drive.file`. These scopes provide **read access across Drive** plus **write access to files created/opened by the app**, not unrestricted modification of arbitrary legacy files. Depending on Google policy and deployment type, restricted scopes can require additional verification. No password is used to connect to Google.

### Test verified without Android SDK

```bash
bash tools/test-core.sh
```

This compiles the portable Kotlin core with `kotlinc` and tests storage reserves, CSV input, MBOX metadata detection, split/reassemble and tamper-detection.

## Security and recovery

- SQLite account metadata is local but **not fully encrypted**. Only vault secrets, OAuth state and resumable-session tokens have AES-GCM Keystore protection. A full encrypted SQLite/SQLCipher migration is required before claiming full-at-rest encryption.
- Portable exports are encrypted **as a whole**, and include saved credentials; never share the `.ovb` file and passphrase together.
- Android Keystore keys are not automatically portable. Restore uses the password-protected export, not raw database copying.
- UI disables screenshots using `FLAG_SECURE` but cannot guarantee resistance to compromised devices.
- An email detected from MBOX is probable evidence only, not a verified active registration.
- No server, tracking or ads included.
- Avoid using cloud token credentials or credential exports in GitHub issues, logs, or version control.

## Source structure

```text
app/src/main/java/org/onevault/app/
  ui/            Native Android screens & dialogs
  data/          SQLite models, file index, account/platform links
  security/      Device Keystore vault, encrypted recovery backup
  cloud/         Google OAuth and Google Drive REST adapter
  work/          Persistent resumable upload worker
  core/          Portable quota, CSV, email evidence, chunk integrity
.github/workflows/android.yml    Android APK workflow
docs/PRD_AR.md                  Original v1.1 PRD
```

## GitHub repository privacy

Create a **private** repository named `OneVault` under your GitHub account. Do not initialize with a README (it is already included). See [`docs/GITHUB_SETUP.md`](docs/GITHUB_SETUP.md). GitHub repository: `amroquqa99-art/OneVault` (private).

## Documentation

- [Full Arabic PRD](docs/PRD_AR.md)
- [Architecture and threat model](docs/ARCHITECTURE.md)
- [Implementation status and blockers](docs/IMPLEMENTATION_STATUS.md)
- [GitHub initialization instructions](docs/GITHUB_SETUP.md)
- [Testing checklist](docs/TEST_PLAN.md)
- [Security policy](SECURITY.md)

**Licensing:** This project is prepared for private ownership. Public licensing has not been selected (`LICENSE-NOTICE.md`).

### Convenience scripts

```bash
bash tools/init-private-repo.sh  # runs on your computer; requires authenticated GitHub CLI
bash tools/build-apk.sh          # runs on your computer; requires Android SDK + Gradle
```

## Project-wide engineering rule

All future changes must follow [AGENTS.md](AGENTS.md). The reusable standard
for other projects is in [docs/PROJECT_STANDARD.md](docs/PROJECT_STANDARD.md).

### Build status as of 2026-10-18

GitHub Actions runs failed *before a runner started* (`steps: []`,
`runner_id: 0`). No Android APK has been produced or installed/tested. See
[GitHub Actions diagnostics](docs/GITHUB_ACTIONS_DIAGNOSTICS.md).

## v0.2.3 alpha changes

- Added Android API-compatible bounded stream reading for imported account CSV and encrypted backup restore (avoids Java 9 `readNBytes` on older Android APIs).
- Added immutable per-account inventory summaries (confirmed/probable/unknown), platform-category counts and associated portable unit tests.
- Added connected-network constraint and exponential backoff for WorkManager uploads; terminal retries are marked `failed`.
- Optimized CSV duplicate detection and moved account relationship lookup out of each account-card render.
- Re-ran portable Kotlin core smoke tests successfully. **Android compilation is still unverified** because the account-level GitHub Actions run fails before runner allocation. See `docs/GITHUB_ACTIONS_DIAGNOSTICS.md`.
