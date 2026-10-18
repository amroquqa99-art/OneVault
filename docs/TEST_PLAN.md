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
