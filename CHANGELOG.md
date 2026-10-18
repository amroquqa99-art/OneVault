# Changelog

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
