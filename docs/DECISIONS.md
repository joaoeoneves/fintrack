# Design decisions

Short records of the main architectural choices: context, decision, consequences.

## 1. Firestore only (no Room) for signed-in users

- **Context:** The app needs sync across devices and offline use. A Room layer on top of Firestore would
  mean two sources of truth and a sync engine to maintain.
- **Decision:** Use Firestore's built-in persistent cache as the only store for signed-in users.
- **Consequences:** Much less code and no conflict handling of our own. Queries are limited to what
  Firestore supports, so most aggregation (totals, plan math) is done in the domain layer from snapshot
  flows. Writes are not awaited, so saving works offline.

## 2. Guest mode backed by a JSON file

- **Context:** Requiring a Google account before trying the app is a barrier, and CI emulators cannot
  sign in anyway.
- **Decision:** "Continue as guest" stores everything in one versioned JSON file (kotlinx.serialization),
  rewritten atomically, with no Firebase involved.
- **Consequences:** The whole app is usable offline without an account and testable end to end on CI. It
  adds a second storage implementation to keep in step with Firestore, and needs backup and migration
  paths so guest data is not stranded.

## 3. Session-switched repositories

- **Context:** ViewModels should not care whether data is local or remote, and a guest must not start
  Firestore.
- **Decision:** Domain repository interfaces are bound to `Session*Repository` delegates that pick the
  local or Firestore implementation from the current session (lazily creating the remote one).
- **Consequences:** Screens are identical in both modes and sign-in switches data sources live. Each
  repository needs three implementations (Firestore, local, fake), kept honest by shared tests.

## 4. Deterministic ids for recurring transactions

- **Context:** Confirming a recurring occurrence must never create duplicates, even with a double tap or two
  devices.
- **Decision:** The generated expense or income gets the id `rec_<itemId>_<yyyyMMdd>`, written in one
  batch with the item's `handledThrough` cursor, instead of storing a `recurringId` on the transaction.
- **Consequences:** Idempotence comes for free from document identity, and transactions keep their
  plain shape (rules and CSV stay unchanged). The link to the template is implicit in the id.

## 5. A global monthly plan built on category budgets

- **Context:** The goal is a "safe to spend" number like the original spreadsheet, without maintaining a
  separate plan per month.
- **Decision:** One list of expected-income lines plus the existing per-category budgets form the plan;
  each month is evaluated from them and the month's actual data.
- **Consequences:** Setup is small and budgets have one meaning. There are no per-month plan overrides
  yet, and the 10-line income cap is enforced only on the client because rules cannot count documents.

## 6. Minimum SDK 26

- **Context:** The app uses `java.time` throughout and modern platform APIs (per-app locales, Credential
  Manager).
- **Decision:** Raise `minSdk` from 24 to 26 in v2.0.
- **Consequences:** No core-library desugaring for `java.time` is needed. Android 7.x devices are no longer
  supported.

## 7. Tag-driven signed releases and Obtainium instead of Google Play

- **Context:** This is a personal, open-source project; a store listing adds fees and review overhead.
- **Decision:** Pushing a `vX.Y` tag runs the release workflow: tests, lint, a signed R8 build, signature
  verification, then a GitHub release with the APK and mapping file. Users update through Obtainium.
- **Consequences:** Releases are reproducible from CI and need no manual steps. There is no store
  distribution or staged rollout, and the signing key is held in repository secrets.

## 8. End-to-end tests on CI emulators

- **Context:** Local emulators tie testing to one machine, and Google sign-in cannot be scripted.
- **Decision:** Maestro flows run on an emulator in GitHub Actions (manual and weekly), written against
  guest mode. The baseline profile is generated the same way.
- **Consequences:** E2E coverage does not depend on a developer machine. Flows that need a Google
  account are not covered on CI, and emulator runs are slow, so they are kept out of the per-PR job.
