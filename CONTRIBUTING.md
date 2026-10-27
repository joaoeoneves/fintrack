# Contributing

Thanks for your interest in FinTrack. Issues and pull requests are welcome.

## Setup

1. Install JDK 21 and an Android SDK (`ANDROID_HOME`).
2. Follow "Getting started" in the [README](README.md): you need your own Firebase project and a
   `google-services.json` in `app/` (gitignored, never commit it).
3. `./gradlew installDebug` to run on a device or emulator. Guest mode needs no sign-in.

## Before you push

Run the shared quality gate from the repository root:

```bash
scripts/gate.sh fast    # ktlint + detekt + unit tests
scripts/gate.sh full    # everything CI checks (lint, release build, androidTest compile, baseline profile build)
```

Follow the existing structure (`ui/`, `domain/`, `data/`, `di/`), MVVM with `StateFlow`, and Kotlin
conventions; ktlint and detekt are enforced. Add or update unit tests under `app/src/test` for logic you
change. If you touch `firestore.rules`, keep the client-side limits in step with it.

## Commits and pull requests

- Use [Conventional Commits](https://www.conventionalcommits.org/): `feat(scope): ...`, `fix: ...`,
  `docs: ...`, `test: ...`, `chore(deps): ...`.
- Branch from `main`, keep PRs focused, and fill in the pull request template.
- CI must pass before merge. Never commit secrets or real financial data; screenshots must use demo data.

## Reporting bugs and ideas

Use the issue forms. For security problems see [SECURITY.md](SECURITY.md).
