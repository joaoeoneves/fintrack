# FinTrack

[![CI](https://github.com/joaoeoneves/fintrack/actions/workflows/ci.yml/badge.svg)](https://github.com/joaoeoneves/fintrack/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Firebase](https://img.shields.io/badge/Firebase-Auth%20%2B%20Firestore-FFCA28?logo=firebase&logoColor=black)

<p align="center">
  <img src="docs/screenshots/dashboard.png" width="200" alt="Dashboard with balance, spending breakdown and budgets">
  <img src="docs/screenshots/recent.png" width="200" alt="Recent expenses and income">
  <img src="docs/screenshots/all-expenses.png" width="200" alt="Expense list with period filter, search and sort">
  <img src="docs/screenshots/add-expense.png" width="200" alt="Add expense form">
</p>

A monthly expense- and income-tracking Android app, built with Kotlin, Jetpack Compose and Firebase.

---

## Table of contents

- [The app](#the-app)
  - [Features](#features)
  - [Tech stack](#tech-stack)
  - [Architecture](#architecture)
  - [Data model](#data-model--firestore)
  - [Download](#download)
  - [Getting started](#getting-started)
  - [Common commands](#common-commands)
  - [Testing](#testing)
  - [CI](#ci)
- [License](#license)

---

## The app

### Features

- **Expense tracking** — add/edit/delete expenses with a name, amount, category, date, and an optional
  note. Five built-in categories (Transfer, Investments, Shopping, Food, Recurring) plus your own.
- **Income tracking** — a separate, parallel entity (free-text source like "Salary" or "Freelance" instead
  of a fixed category), kept intentionally independent of expenses so it could be added later with zero
  risk to the existing expense code.
- **Dashboard** — time-range filters (1 week / 1 month / 3 months / 6 months / 1 year), a pie chart of
  spend by category, the last 5 expenses and last 5 income entries, and a **net balance** figure (income
  minus expenses) for the selected range.
- **Per-category budget limits** — set a monthly limit per category; the Dashboard shows progress bars
  against the current calendar month (independent of the rolling time-range filter) with an over-budget
  visual state.
- **Full expense/income lists** — the same time-range filters, text search, and sorting (newest/oldest
  first, highest/lowest amount); swipe-to-dismiss delete with an Undo snackbar, plus a trash-icon button
  for discoverability.
- **CSV import/export** — for expenses, from Settings (income CSV support is a deliberate fast-follow, not
  yet built).
- **Google Sign-In** via Credential Manager (not the legacy `GoogleSignInClient` API) + Firebase Auth.
- **Settings** — reached from the Dashboard's gear icon:
  - **Theme**: system default, light or dark, with a full, intentional Material 3 dark color scheme (not an
    auto-inverted afterthought).
  - **Language**: English or Portuguese (or the system language), switched in-app. Theme, language and
    currency are stored locally (DataStore) and synced across your devices through Firestore.
  - **Display currency**: USD, EUR, GBP, JPY, CAD or AUD. Purely cosmetic: it changes the symbol and
    formatting, with no exchange-rate conversion.
  - **Categories**: add your own (name, icon, color, spending or saving), rename/recolor the built-ins,
    reorder, archive (hidden from pickers, kept on existing data) or delete unused ones.
  - CSV import/export and the signed-in account.
- **Offline-friendly** — Firestore's built-in local cache is the only persistence layer; there's no local
  Room database, so data survives offline use and syncs back once reconnected.

### Tech stack

| Layer | Choice |
|---|---|
| Language | Kotlin 2.4 |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM — ViewModels hold state/logic, composables stay declarative |
| Async | Coroutines + `Flow`, `StateFlow` for UI state (`collectAsStateWithLifecycle`) |
| DI | Hilt |
| Navigation | Compose Navigation, type-safe routes |
| Auth | Firebase Auth + Google Sign-In via `androidx.credentials` (Credential Manager) |
| Persistence | Firebase Firestore (no Room/local database) + Preferences DataStore for theme/currency/language |
| Lint/format | ktlint + detekt |
| Unit tests | JUnit4 + Robolectric (where Android framework classes are needed, e.g. the legacy `SharedPreferences` migration) |
| E2E tests | [Maestro](https://maestro.mobile.dev) |
| CI | GitHub Actions |

Min SDK 24, target/compile SDK 36, Java 11 source/target compatibility.

### Architecture

Single module (`:app`), package `com.joaoeoneves.fintrack`, organized as:

```
app/src/main/java/com/joaoeoneves/fintrack/
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
  amountCents: number       // integer minor units, always > 0 — avoids float rounding errors
  category: string          // category id: a built-in (TRANSFER | INVESTMENTS | SHOPPING | FOOD |
                            // RECURRING) or a users/{uid}/categories document id
  isRefund: boolean         // optional, absent = false; a refund subtracts from its category
  date: timestamp
  note: string | null
  createdAt / updatedAt: timestamp

users/{uid}/income/{incomeId}
  source: string            // free text, e.g. "Salary", "Freelance" — not a fixed enum
  amountCents: number
  date: timestamp
  note: string | null
  createdAt / updatedAt: timestamp

users/{uid}/budgets/{categoryId}
  limitCents: number
  updatedAt: timestamp

users/{uid}/categories/{categoryId}   // user categories (generated ids) and optional overrides
  name: string | null       //   of the built-ins (same id; null name = localized default)
  iconKey / colorKey: string
  kind: string              // SPENDING | SAVING
  order: number
  archived / builtIn: boolean
  createdAt / updatedAt: timestamp

users/{uid}/settings/preferences       // theme, currency and language, synced across devices
  theme: string             // SYSTEM | LIGHT | DARK
  currency: string          // USD | EUR | GBP | JPY | CAD | AUD
  language: string          // SYSTEM | ENGLISH | PORTUGUESE
  updatedAt: timestamp
```

Security rules ([`firestore.rules`](firestore.rules)) restrict every collection to
`request.auth.uid == userId` — a user can only ever read/write their own documents.

### Download

A debug-signed APK of the latest version is attached to the
[latest release](https://github.com/joaoeoneves/fintrack/releases/latest) (Android 7.0+).

### Getting started

1. **Firebase project**: create one, register an Android app with package `com.joaoeoneves.fintrack`,
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
   Robolectric is used where a real Android framework class is unavoidable (e.g. the legacy `SharedPreferences` migration).
2. **Instrumented tests** (`app/src/androidTest`) — on-device/emulator, currently minimal; most real
   on-device verification happens through Maestro instead (see below).
3. **Maestro E2E flows** (`.maestro/*.yaml`) — YAML flows driven against a real emulator, using semantic
   element matching (by visible text/content-description) rather than brittle coordinates or resource IDs.
   Run with `maestro test .maestro/<flow>.yaml`. Existing flows: `golden-path.yaml` (add expense → dashboard → full list → edit →
   delete → filters), `income-tracking.yaml` (income add → net balance → delete/undo), `settings.yaml`
   (theme and currency switching), `date-field.yaml` (date picker) and `swipe-delete-check.yaml`
   (swipe-to-delete and Undo). All of them start from an already signed-in session, since Google Sign-In
   can't be scripted headlessly.

### CI

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on every push/PR to `main`: checkout → JDK 21 →
Gradle → `ktlintCheck` → `detekt` → `testDebugUnitTest` → `assembleDebug`. Since `app/google-services.json`
is a real, gitignored credential, CI writes a shape-compatible **placeholder** (fake project ID, fake OAuth
client, fake API key) as a workflow step — just enough for the `google-services` Gradle plugin's presence
check and for compiling, since unit tests run against Fake repositories and never make real Firestore
calls. Instrumented/Maestro tests are intentionally kept out of this CI job (booting an emulator in CI is
slow and flake-prone) and are run manually instead.

---

## License

[MIT](LICENSE) © 2026 João Neves
