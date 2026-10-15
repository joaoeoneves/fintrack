# Contributing

Thanks for your interest in FinTrack.

## How this repository works

This repository is the public home of FinTrack: the source of every released version and the APKs. Each version
arrives as one commit (`FinTrack vX.Y`) whose message is that version's [CHANGELOG](CHANGELOG.md) entry.
Day-to-day development happens elsewhere, so `main` only moves when a version is released.

- **Issues are welcome**: bugs, ideas and questions. Use the issue forms.
- **Pull requests** can't be merged as they are, because the next release replaces `main`. A good change is
  applied by hand in the next version, with you credited as co-author, and the pull request is closed with a
  link to that release. For anything bigger than a small fix, open an issue first.

## Building it

1. Install JDK 21 and an Android SDK (`ANDROID_HOME`).
2. Follow "Getting started" in the [README](README.md): you need your own Firebase project and a
   `google-services.json` in `app/` (gitignored, never commit it).
3. `./gradlew installDebug` to run on a device or emulator. Guest mode needs no sign-in.

Before sending a change, run the shared quality gate from the repository root:

```bash
scripts/gate.sh fast    # ktlint + detekt + unit tests
scripts/gate.sh full    # also Android lint, the R8 release build and androidTest compilation
```

Follow the existing structure (`ui/`, `domain/`, `data/`, `di/`), MVVM with `StateFlow`, and Kotlin conventions;
ktlint and detekt are enforced. Add or update unit tests under `app/src/test` for logic you change. If you touch
`firestore.rules`, keep the client-side limits in step with it. Never commit secrets or real financial data;
screenshots must use demo data.

For security problems see [SECURITY.md](SECURITY.md).
