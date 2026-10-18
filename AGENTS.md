# Mandatory engineering policy for OneVault coding agents

1. **Docker-first:** all build, test, lint, packaging and auxiliary services MUST have an isolated Docker / Docker Compose workflow. The host must not require project-specific JDK, Android SDK, Gradle, Node or Python dependencies (except Docker Engine/Desktop and Compose v2).
2. **Version pinning:** pin image versions, runtime/toolchain versions and dependencies. Keep local and CI instructions consistent. Avoid floating `latest` tags.
3. **One-command verification:** maintain `bash tools/docker-build.sh build` and `test`/`check`. Document where the APK is produced.
4. **Secrets stay out:** never commit passwords, OAuth refresh tokens, signing keys or private vault backups. Build images must not copy secrets. Use local ignored configuration or appropriate CI secrets.
5. **Reproducibility:** preserve source and configs, record dependency provenance and keep cache volumes disposable. A clean container must be able to build given internet access to approved dependency registries.
6. **Data safety:** test recovery and integrity before considering personal-password or cloud-storage workflows production ready. Do not label uncompiled code as working.
7. **Provider policy:** do not add API quota circumvention logic or unsupported TeraBox endpoints. Prefer official connectors, explicit permissions, and narrow OAuth scopes.
8. **Android distinction:** the build runs in Docker; an installed Android APK runs on-device. An emulator/hardware-device test may still need hardware capabilities and Android-specific authorization.
9. **Failure transparency:** CI/build failures must be documented. Never claim an APK exists without checking the exact binary.

Copy this policy to the root `AGENTS.md` of future projects as a template;
adapt the commands for the project's runtime and deployment environment.