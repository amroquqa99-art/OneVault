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

## GitHub verification (2026-10-18)

- Private GitHub repository source pushed and verified; Docker and all Android source files present.
- `AGENTS.md` enforces Docker-first rule; `docs/PROJECT_STANDARD.md` is reusable across projects.
- New JUnit tests in `app/src/test/` were committed but **could not execute** because GitHub Actions failed before runner provisioning.
- Runner was not allocated (`runner_id: 0`, zero steps). Root cause is not established. See `docs/GITHUB_ACTIONS_DIAGNOSTICS.md`.

## v0.2.3 alpha follow-up

- Android API-level compatibility: bounded file and backup reads use custom `BoundedIo` rather than Java 9 `InputStream.readNBytes`.
- Identity insights show confirmed/probable/unknown links with category counts; pure Kotlin tests pass.
- Upload worker now requires network connectivity and has explicit exponential backoff; failed retries show a terminal failure state.
- Account import eliminates O(N²) duplicate lookups.
- Running `bash tools/test-core.sh` locally passed. Android SDK/Docker are not present in the current execution runtime, and GitHub-hosted jobs were rejected before a runner starts. An APK still **does not exist**; user must resolve the GitHub Actions runner issue or run the Docker build on a working Docker host.

## v0.2.4 alpha hardening (2026-10-18)

- Fixed imported email evidence overwriting stronger manually confirmed identity links.
- Fixed upload allocation ignoring earlier jobs queued against the same provider account.
- Rejects new Google uploads when quota is unknown rather than assuming unlimited space.
- Rejects malformed/missing resumable upload acknowledgement without advancing checkpoint.
- Serialized OAuth refresh operations per account to avoid racing rotated token state.
- Pure Kotlin smoke tests pass including out-of-bounds Range and quota overflow cases.
- Google Actions remains at runner-provisioning failure with `runner_id: 0`, `steps: []`; no Android APK was produced or verified.

## v0.2.5 alpha — local data-safety hardening

- Local split/assemble now refuses existing destination files or occupied chunk folders; test added to guard against replacement of prior files.
- MBOX/EML scanner ignores message bodies, including spoofed-looking `From:` and `Subject:` lines; messages remain **probable** evidence.
- Added offline platform-category hints (`Development`, `Productivity`, etc.) and boundary-safe matching for known hostnames.
- Safe-quota math saturates without 64-bit overflow and upload completion is checked explicitly.
- Portable Kotlin core smoke tests pass. **Android app still has not been compiled**, and no APK has been validated on a device; the registered OAuth flow has not been integration-tested.
- Docker-first builds remain configured in `Dockerfile`, `compose.yaml` and `tools/docker-build.sh`. GitHub-hosted runner allocation is an external blocker (no runner ID, no steps).

## v0.2.5 alpha — saved-login discovery

- User-exported Google Password Manager CSV (`url`, `username`, optional `name`) can add **probable** platform links to emails already registered.
- Passwords and notes are not retained as fields or written to the vault; stream input is bounded. No Gmail permission is used.
- The plaintext original CSV still needs secure handling and deletion after import.
- Pure Kotlin parser smoke tests pass; Android/UI/device testing is still outstanding.
