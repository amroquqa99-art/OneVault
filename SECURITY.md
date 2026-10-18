# Security policy

- Never commit real Google tokens, access credentials, user identifiers, exported CSV or backups.
- Android Keystore protects OAuth state and vault secrets at rest; uninstalling removes the key. Keep encrypted recovery backups.
- This is alpha software, unaudited. Do not store your only copy of a password or irreplaceable file here.
- Never publish a private repository without reviewing its history for credentials.
- For reports, open a private GitHub vulnerability report after the repository is created.
- The OAuth redirect URI must match your Google Cloud OAuth configuration.
- No implicit storage quota bypass, no scrapers or unsupported provider login endpoints.
