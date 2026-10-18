# GitHub Actions: pre-run failure (2026-10-18)

Private repository: `amroquqa99-art/OneVault`.

Multiple workflow attempts, including the v0.2.3-alpha commit, terminated
within seconds **before a GitHub runner was provisioned**. The GitHub Jobs API
reported `runner_id: 0` and `steps: []` for the jobs we inspected. This means
we cannot derive an Android compiler error from these failures. The root cause
is unknown from the available logs.

Representative observed runs:
- https://github.com/amroquqa99-art/OneVault/actions/runs/39142558268 (v0.2.3-alpha: no runner, no steps)
- https://github.com/amroquqa99-art/OneVault/actions/runs/39140515274
- https://github.com/amroquqa99-art/OneVault/actions/runs/39140698580
- https://github.com/amroquqa99-art/OneVault/actions/runs/39140853887

## Owner checks

1. Open the latest run in GitHub Actions and read any banner about workflow
   permissions, billing/usage, runner allocation or account restrictions.
2. Review Settings > Actions > General in the repository; verify Actions can run.
3. Inspect the account's Settings > Billing / Actions usage if GitHub indicates
   a spending, payment, or private-repository runner problem.
4. Re-run `Android (Docker)` once the account/runner issue is resolved.
5. Only download and install `OneVault-debug-apk` if the workflow passes and
   the artifact actually exists. Test on a disposable/test device and account.

## Local alternative

Any host with Docker Engine/Desktop and Compose v2 can run:

```sh
bash tools/docker-build.sh test
bash tools/docker-build.sh build
```

An APK is produced only if the build succeeds at
`app/build/outputs/apk/debug/app-debug.apk`.

**Release status:** No compiled or verified APK at this checkpoint. Core JVM
smoke tests pass, but Android compilation and device tests remain incomplete.
