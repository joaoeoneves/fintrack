# AgenticUsage

A monthly expense- and income-tracking Android app, built with Kotlin, Jetpack Compose and Firebase.

---

## Table of contents

- [The app](#the-app)
  - [Features](#features)
  - [Tech stack](#tech-stack)
  - [Architecture](#architecture)
  - [Data model](#data-model--firestore)
  - [Getting started](#getting-started)
  - [Common commands](#common-commands)
  - [Testing](#testing)
  - [CI](#ci)

---

## The app

### Features

- **Expense tracking** — add/edit/delete expenses with a name, amount, one of four fixed categories
  (Transfer, Investments, Shopping, Recurring), date, and an optional note.
- **Income tracking** — a separate, parallel entity (free-text source like "Salary" or "Freelance" instead
  of a fixed category), kept intentionally independent of expenses so it could be added later with zero
  risk to the existing expense code.
- **Dashboard** — time-range filters (1 week / 1 month / 3 months / 6 months / 1 year), a pie chart of
  spend by category, the last 5 expenses and last 5 income entries, and a **net balance** figure (income
  minus expenses) for the selected range.
- **Per-category budget limits** — set a monthly limit per category; the Dashboard shows progress bars
  against the current calendar month (independent of the rolling time-range filter) with an over-budget
  visual state.
- **Full expense/income lists** — swipe-to-dismiss delete with an Undo snackbar, plus a trash-icon button
  for discoverability.
- **CSV import/export** — for expenses (income CSV support is a deliberate fast-follow, not yet built).
- **Google Sign-In** via Credential Manager (not the legacy `GoogleSignInClient` API) + Firebase Auth.
- **Dark mode** — a full, intentional Material 3 dark color scheme (not an auto-inverted afterthought),
  following the system setting by default, with a manual light/dark override in the Dashboard's overflow
  menu.
- **Offline-friendly** — Firestore's built-in local cache is the only persistence layer; there's no local
  Room database, so data survives offline use and syncs back once reconnected.

### Tech stack

| Layer | Choice |
|---|---|
| Language | Kotlin 2.2 |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM — ViewModels hold state/logic, composables stay declarative |
| Async | Coroutines + `Flow`, `StateFlow` for UI state (`collectAsStateWithLifecycle`) |
| DI | Hilt |
| Navigation | Compose Navigation, type-safe routes |
| Auth | Firebase Auth + Google Sign-In via `androidx.credentials` (Credential Manager) |
| Persistence | Firebase Firestore only — no Room/local database |
| Lint/format | ktlint + detekt |
| Unit tests | JUnit4 + Robolectric (where Android framework classes are needed, e.g. `SharedPreferences`) |
| E2E tests | [Maestro](https://maestro.mobile.dev) |
| CI | GitHub Actions |

Min SDK 24, target/compile SDK 36, Java 11 source/target compatibility.

### Architecture

Single module (`:app`), package `ptech.joaoe.agenticusage`, organized as:

```
app/src/main/java/ptech/joaoe/agenticusage/
├── ui/            Compose screens and components (dashboard, expense/, income/, auth/, theme/, ...)
├── data/          Repository implementations (Firestore + Fake), CSV import/export
├── domain/        Domain models and repository interfaces
├── di/            Hilt modules
app/src/test/          Unit tests (JVM, mirrors the app/src/main package structure)
app/src/androidTest/   Instrumented tests
.maestro/              E2E flows (YAML), run against a real emulator/device
```

Each repository has a real Firestore-backed implementation and a `Fake*` in-memory implementation (used in
unit tests), bound via Hilt in `di/RepositoryModule.kt`. Cross-cutting state (auth, theme preference) is
resolved once in `MainActivity` and threaded down through the nav graph as plain callback/value parameters
— not re-resolved via `hiltViewModel()` inside individual nav destinations, which could otherwise pick up a
different, nav-entry-scoped instance.

### Data model (Firestore)

```
users/{uid}/expenses/{expenseId}
  name: string
  amountCents: number       // integer minor units — avoids float rounding errors
  category: string          // TRANSFER | INVESTMENTS | SHOPPING | RECURRING
  date: timestamp
  note: string | null
  createdAt / updatedAt: timestamp

users/{uid}/income/{incomeId}
  source: string            // free text, e.g. "Salary", "Freelance" — not a fixed enum
  amountCents: number
  date: timestamp
  note: string | null
  createdAt / updatedAt: timestamp

users/{uid}/budgets/{categoryName}
  limitCents: number
  updatedAt: timestamp
```

Security rules ([`firestore.rules`](firestore.rules)) restrict every collection to
`request.auth.uid == userId` — a user can only ever read/write their own documents.

### Getting started

1. **Firebase project**: create one, register an Android app with package `ptech.joaoe.agenticusage`,
   enable Google Sign-In in Authentication, create a Firestore database, and deploy `firestore.rules`.
   Download `google-services.json` into `app/` — it's gitignored and never committed (CI uses a
   placeholder; see [CI](#ci)).
2. **SHA-1**: add your debug (and release) signing certificate's SHA-1 to the Firebase Android app config
   (required for Google Sign-In) — `./gradlew signingReport` prints it.
3. **JDK 21** and `ANDROID_HOME` pointing at an installed Android SDK.
4. Build and run:
   ```
   ./gradlew assembleDebug
   ./gradlew installDebug   # with a device/emulator connected
   ```

### Common commands

Run from the repository root via the Gradle wrapper:

| Command | Purpose |
|---|---|
| `./gradlew assembleDebug` | Build debug APK |
| `./gradlew assembleRelease` | Build release APK |
| `./gradlew installDebug` | Install debug build on a connected device/emulator |
| `./gradlew test` | Run JVM unit tests (`app/src/test`) |
| `./gradlew test --tests "..."` | Run a single unit test |
| `./gradlew connectedAndroidTest` | Run instrumented tests (needs a connected device/emulator) |
| `./gradlew ktlintCheck` | Lint (formatting) |
| `./gradlew detekt` | Lint (static analysis) |
| `./gradlew check` | Full check: lint + tests |
| `./gradlew clean` | Clean build outputs |

### Testing

Three layers, deliberately kept separate:

1. **Unit tests** (`app/src/test`) — JVM, run against `Fake*Repository` implementations, no device needed.
   Robolectric is used where a real Android framework class is unavoidable (e.g. `SharedPreferences`).
2. **Instrumented tests** (`app/src/androidTest`) — on-device/emulator, currently minimal; most real
   on-device verification happens through Maestro instead (see below).
3. **Maestro E2E flows** (`.maestro/*.yaml`) — YAML flows driven against a real emulator, using semantic
   element matching (by visible text/content-description) rather than brittle coordinates or resource IDs.
   Run with `maestro test .maestro/<flow>.yaml`. Existing flows: `golden-path.yaml` (sign-in → add expense → dashboard → full list →
   edit → delete → filters) and `income-tracking.yaml` (income add → net balance → delete/undo).

### CI

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on every push/PR to `main`: checkout → JDK 21 →
Gradle → `ktlintCheck` → `detekt` → `testDebugUnitTest` → `assembleDebug`. Since `app/google-services.json`
is a real, gitignored credential, CI writes a shape-compatible **placeholder** (fake project ID, fake OAuth
client, fake API key) as a workflow step — just enough for the `google-services` Gradle plugin's presence
check and for compiling, since unit tests run against Fake repositories and never make real Firestore
calls. Instrumented/Maestro tests are intentionally kept out of this CI job (booting an emulator in CI is
slow and flake-prone) and are run manually instead.

