# Changelog

## 0.2.6-alpha — 2026-10-18

- Add combined Docker Android lint + unit tests + APK output command and SHA-256 artifact verification.
- Reduce redundant CI build invocations and keep generated output in a single artifact.
- Derive Android Google OAuth custom redirect scheme from the client ID when provided.
- Document Google's Android custom redirect restrictions and device-side requirements.
- Add Android manifest / build workflow source preflight for offline environments.
- Core Kotlin smoke tests and offline source preflight passed; full Android build blocked by missing toolchain and network.



## v0.2.5-alpha — 2026-10-18

- Added metadata-only Google Password Manager CSV site discovery; passwords and notes are not imported.
- Added bounded streaming parser with duplicate suppression and matching to existing email identities.
- Prevented accidental overwrite of existing output files during local chunk reassembly: atomic no-clobber hard-link publication after checksums pass.
- Prevented local split operations from reusing nonempty chunk directories; partial chunks are cleaned up after errors.
- Prevented malicious or malformed mail-message body lines from being mistaken for registration headers during EML/MBOX evidence discovery.
- Added deterministic on-device service-category suggestions with conservative domain-boundary matching; never promotes heuristic email evidence to a confirmed account.
- Corrected saturating safe-quota arithmetic for extreme 64-bit values.
- Added extra pure Kotlin regression cases for file protection, category hints and mail-body header spoofing.
- Upload worker now retains final server-completion confirmation and handles 308 status checks conservatively.

**Verification:** `bash tools/test-core.sh` passes on a local JDK/Kotlin runtime. Full Android Gradle compilation and installation have not been verified; GitHub Actions runner has not been provisioned. Do not trust this alpha build for primary credentials or irreplaceable files.


## v0.2.4-alpha — 2026-10-18

- Enforced quota checks for unknown provider capacity and earlier in-flight upload reservations.
- Hardened Google resumable upload Range parsing; ambiguous server responses now trigger a safe retry.
- Preserved manually confirmed platform relationships during MBOX/EML reimports.
- Serialized Google OAuth token refresh on a per-account basis.
- Added regression tests and updated Docker build documentation. Core smoke tests pass; full Android build still blocked by unavailable build runners and SDK locally.


## 0.2.3-alpha — 2026-10-18

- Bound CSV and encrypted-backup input streams without `InputStream.readNBytes` (Android API 26 compatibility).
- Add account-to-platform classification and evidence-confidence summaries.
- Add connected-network constraint and exponential backoff to background uploads.
- Optimize account CSV deduplication and account analytics queries.
- Extend portable and JUnit tests for the new core logic.

**Build status:** Kotlin portable core tested. Android APK not yet built; GitHub Actions jobs did not receive a runner. Not ready for personal credential storage.

## 0.2.2-alpha — 2026-10-18

- Initial Android Kotlin project, local identity registry, Drive adapter scaffold, encrypted vault, evidence scanner and resumable upload source.
- Docker / Docker Compose Android build environment, GitHub Actions workflow and Docker-first project engineering standard.
