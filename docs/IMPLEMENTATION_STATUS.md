# Delivery status — 2026-10-18

| Capability | State | Notes |
| --- | --- | --- |
| PRD v1.1 | Included | `docs/PRD_AR.md` |
| Android project structure | Implemented in source | No SDK/Gradle to compile locally |
| Account registry + categories + reserve | Coded, not device-tested | SQLite |
| Platform directory and evidence levels | Coded, not device-tested | Manual relations |
| CSV account import | Coded; parser tested | No password CSV import |
| MBOX/EML evidence scanning | Coded; parser tested | Heuristic only; not live Gmail |
| Device encrypted credential vault | Coded, not security-audited | Keystore AES-GCM |
| Password-protected recovery | Coded, not device-tested | PBKDF2 + AES-GCM |
| Google OAuth + signed-in email verification | Coded, not end-to-end tested | Needs owner OAuth client ID and redirect |
| Google Drive quota/list/download | Coded, not end-to-end tested | Needs real OAuth & Drive API |
| Google Drive upload / resume | Coded, not end-to-end tested | SAF + WorkManager + resumable sessions |
| Local split/reassemble integrity | Core tested | Not cloud-distributed |
| TeraBox | Not implemented | Official access needed |
| Automated Gmail discovery | Not implemented | Requires restricted scope / OAuth |
| Google-account automated pooling | Not implemented | Terms constraint |
| Single browser link for distributed file | Not implemented | Requires server or compatible peer protocol |
| Android APK binary | Not yet produced | Docker build configured; cannot run Docker in this environment |
| New private GitHub repository | Exists | `amroquqa99-art/OneVault` confirmed private; source sync status must be checked |

## Test evidence

`tools/test-core.sh` successfully completed locally using Kotlin CLI.

## Known engineering debts

- Kotlin/Android dependency resolution and actual app build must be verified.
- UI lacks pagination in visible file list and may be slow with large index.
- `MailEvidence` handles common MBOX headers; MIME-encoded subjects and public suffixes need better parsing.
- No full database encryption, as explicitly described in README.
- Foreground upload progress notification is currently limited to WorkManager; Android may defer work depending on conditions.
- Upload worker does not create an index record until a manual refresh.
- Uploads do not yet implement proactive per-account concurrent pending byte reservations.
- Backup importer is bounded and encrypted but does not support conflict-resolution UI.
- No Android instrumentation or Google API integration tests were run.

## Docker-first update (2026-10-18)

- Dockerfile, Compose, `.dockerignore`, `tools/docker-build.sh`, and Docker CI workflow added.
- Host setup requires Docker Engine/Desktop and Compose v2, not host Android SDK/Gradle.
- `GoogleAuth` offloads userinfo network verification from the Android main thread.
- Portable Kotlin core smoke tests passed; Android binary build is **unverified** until CI runs successfully.
