# Private GitHub repository

Confirmed user-owned repository: <https://github.com/amroquqa99-art/OneVault> — private.

When developing locally:

```bash
git remote add origin git@github.com:amroquqa99-art/OneVault.git
git branch -M main
git push -u origin main
```

All pushes to `main` compile a debug APK in GitHub Actions using Docker Compose.
Open the Actions run's Artifacts tab for `OneVault-debug-apk` after a successful build.
Release signing keys, OAuth state, password vault files and personal credentials
MUST NOT be committed.
