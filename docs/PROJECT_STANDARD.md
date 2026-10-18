# Reusable Docker-first project standard

This is intended to be reused across future projects, not just OneVault.

## Required deliverables

- Dockerfile(s) with pinned build/runtime versions (and multi-stage build where useful).
- `compose.yaml` for development, build, test, and support services.
- `.dockerignore` and `.gitignore` that exclude secrets and generated outputs.
- Environment variable example (`.env.example`, if needed) with no real secrets.
- Shell scripts or Makefile providing `build`, `test`, `lint/check`, `run`, and `clean` as relevant.
- README commands requiring only Docker Engine/Desktop and Compose v2 on the host; include caveats for hardware capabilities.
- CI pipeline that executes the *same container workflow*, not an entirely separate host-specific script.
- Persistent volumes for stateful services, explicit backup/restore instructions and basic health checks for long-running services.
- Architecture-appropriate security: non-root runtime containers, minimal capabilities, no privileged host mounts, versioned migrations, secrets through mounted files or secrets managers.
- Health and readiness checks, reproducible setup, and actionable failure logs.

## Boundaries

Docker cannot replace the Android runtime or turn Google Drive/TeraBox API policies into local implementation details. Network access, OAuth registration, device signing and app distribution remain separate requirements.
