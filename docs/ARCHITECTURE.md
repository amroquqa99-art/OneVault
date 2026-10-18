# OneVault architecture, data flows and trust boundaries

## User data domains

1. **Identity**: `account`, `platform`, `association`. Local SQLite; normal metadata not encrypted at rest.
2. **Secret vault**: `secret` contents AES-256-GCM via Android Keystore. Passwords never sent to OneVault service.
3. **Cloud authorization**: AppAuth `AuthState` stored AES-GCM via separate local encryption operation; refresh token is never exported.
4. **Cloud cache**: `remote_file` materialized snapshot per account (Google ID, size, name, MIME, webViewLink).
5. **Transfers**: WorkManager jobs reference SAF URI, durable upload session (AES-GCM), byte offset. Upload worker reconnects on re-execution.
6. **Portable recovery**: `OVB1` + 16-byte salt + 12-byte IV + AES-256-GCM ciphertext. Password PBKDF2-HMAC-SHA256, 240k iterations; export includes identities/links/vault secrets, but not provider tokens.

## Trust boundaries

- Google AppAuth: external browser login → OAuth redirect → encrypted state in private app DB.
- Google Drive: only HTTPS with access token; uploaded file content goes from Android SAF through official REST API.
- Source file URI: access permitted by system picker; WorkManager uses persisted read permissions.
- MBOX import: local-only header scanner. It does not send mail data to any endpoint. Domains derived from address heuristics may be wrong (e.g. co.uk, relays).
- Recovery import: user-supplied encrypted file, bounded to 8 MiB, authenticated before parsing. JSON schema 1 only.
- No generic scraper, unofficial Google login handling or TeraBox cookie sharing.

## Next engineering gates

- **Build gate:** Android Studio build passes with JDK 17, SDK 35, dependency versions resolved.
- **Auth gate:** complete registered redirect URI / Android client flow on a device; verify token refresh when access token expires.
- **Data gate:** transactional restore on device and Android Keystore behavior across reinstall; ensure auth and vault secrets remain encrypted in SQL DB.
- **Transfer gate:** cancel, resume after reboot, 308 range handling, quota reserve under concurrent uploads, 401/403/429, 500/503 retry/backoff.
- **Provider gate:** TeraBox connector only after official credentials, capability matrix, integration tests.
- **Share gate:** never distribute refresh tokens or cloud session URLs in share link; use delegated scoped capability + short-lived gateway or app protocol.
- **Crypto gate:** security review, metadata encryption and robust KDF benchmarking (possible future Argon2id).

## Google access limitations

- `drive.readonly`: list and download existing binary files; broader access is restricted.
- `drive.file`: write user-chosen/app-created files. Does not automatically enable arbitrary file writes.
- Google Docs MIME types require `files.export`, not `alt=media`.
- API scope/terms restrictions apply regardless of personal-use intent.

## Sharing and chunking

Pure local `ChunkCodec` is a tested building block. It is **not** wired to cloud account allocation or browser share links. Distributed storage requires a verifiable signed manifest, per-part authenticated encryption, redundancy rules and allowed target providers before it can be exposed to users.
