#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
command -v gh >/dev/null || { echo 'Install GitHub CLI (gh) and sign in using gh auth login'; exit 1; }
command -v git >/dev/null || { echo 'Git not installed'; exit 1; }
gh auth status >/dev/null
if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then git init -b main; fi
if ! git rev-parse HEAD >/dev/null 2>&1; then
  git add .
  git commit -m "feat: initialize OneVault Android alpha"
fi
# Explicit --private: never accidentally create this repo as public.
gh repo create OneVault --private --source=. --remote=origin --push
printf 'Repository created privately. Verify with: gh repo view --json nameWithOwner,visibility\n'
