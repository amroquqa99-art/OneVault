# Device acceptance tests (must run before production use)

1. Launch on Android 12 and Android 15; rotate screen and force-stop/relaunch.
2. Add 30 identities; check duplicate email error and category filtering.
3. Save 10 platform relations; restore encrypted backup on a second device; check records and secrets.
4. Run wrong passphrase test; ensure restore fails without partial database writes.
5. Import CSV containing quoted commas and blank rows; confirm expected counts.
6. Import user-exported Gmail MBOX; check that discoveries stay `probable`, not `confirmed`.
7. Configure Google Cloud OAuth (own signing cert fingerprint and redirect). Authenticate three separate accounts.
8. Sync 1000+ Drive files, including Google-native Docs; open non-native download and Google-native web link.
9. Upload 1 GiB random file; cut Wi-Fi mid-upload; kill/restart app; verify resumed upload checksum.
10. Set reserve to the currently available storage, attempt upload; assert blocked.
11. Disconnect OAuth and confirm cached files become inaccessible remotely but identity metadata remains.
12. Test concurrent transfers and provider 401/403/429/5xx conditions with a test API/proxy.
13. Review database in rooted emulator to confirm secrets and OAuth tokens are ciphertext.
14. Run static code scan, dependency vulnerability audit and security review before saving primary passwords.
15. Confirm app works without analytics or any OneVault backend.

Core unit smoke test: `bash tools/test-core.sh` (requires Kotlin CLI).

## Priority regression cases added in v0.2.4

1. Queue two uploads where only the first fits once account reserve is subtracted: second must not start a resumable Google upload session.
2. Simulate missing `storageQuota.limit`: queue must enter `blocked: unavailable quota` and must not upload.
3. Simulate a Google `308` response without a Range header to an upload chunk: checkpoint must not advance; next retry should query server offset.
4. Simulate malformed or out-of-bounds `Range` headers: reject rather than silently assuming progress.
5. Import a probable GitHub registration from exported email after manual confirmed registration: preserve confidence and original note.
6. Trigger concurrent Google token refresh requests for one account: ensure stored token state is serialized.

Cases 1, 2, 5, 6 need real Android/DB or instrumentation validation; pure decision/parsing functions for 1–5 have JVM tests. Android CI/build is still unavailable due to runner allocation failure.

## New v0.2.5 regression checks

- Splitting into an existing, non-empty chunk directory must fail without altering any original chunks.
- Assembling over a preexisting output must fail, preserving its bytes; corrupted parts must never publish the output.
- MBOX/EML message bodies must not create registration associations even when text contains apparent `From:` and `Subject:` lines.
- Subdomain categorization must classify `accounts.github.com` as Development but reject lookalikes such as `fakegithub.com`.
- Validate safe quota arithmetic at `Long.MAX_VALUE` bounds.
- Actual Android backup/restore, OAuth identity flow, SAF handling, and WorkManager jobs still require a built APK and hardware/emulator tests.
