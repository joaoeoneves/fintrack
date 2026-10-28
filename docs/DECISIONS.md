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

## 5. A global monthly plan built on category budgets (superseded by 10 in 3.0)

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
- **Decision:** The release workflow (run from Actions with a version, or by pushing a `vX.Y` tag) runs: tests, lint, a signed R8 build, signature
  verification, then a GitHub release with the APK and mapping file. Users update through Obtainium.
- **Consequences:** Releases are reproducible from CI and need no manual steps. There is no store
  distribution or staged rollout, and the signing key is held in repository secrets.

## 8. Fast JVM screen tests on every PR; emulator E2E on demand

- **Context:** Emulator runs on CI were slow and flaky, and on a private repository's small runners they
  cost metered minutes and frequently failed to boot. Google sign-in cannot be scripted anyway.
- **Decision:** The main flows run as Robolectric + Compose screen tests on the JVM in every PR. The
  Maestro flows (guest mode) stay in `.maestro/` and run only from the manual E2E workflow or by hand,
  for example as a final smoke test before a major release.
- **Consequences:** PRs get UI coverage in minutes with no emulator. Device-only behaviour (real
  rendering, system dialogs) is checked only when the E2E workflow is run.

## 9. Automation runs on app start, not in the background

- **Context:** Recurring items that post themselves and income recorded on payday should happen without
  a tap, but a background job (WorkManager) means scheduling, permissions and a second code path.
- **Decision:** `AutomationSync` runs the steps when the app starts or returns to the foreground and
  whenever the data they read changes. Each step only fills what is already due and is idempotent, using
  the deterministic recurring ids and per-line `lineThrough` cursors.
- **Consequences:** Nothing is posted while the app stays closed; the next time you open it, everything
  that fell due is caught up. It is simple, safe across two devices, and testable on the JVM.

## 10. Envelopes, savings plans and one income mechanism (3.0)

- **Context:** 2.x had groups, category limits, pots (earmarked income), goals linked to recurring items,
  recurring income and a separate payday switch: several overlapping ways to say the same thing.
- **Decision:** One concept per job. An **envelope** (stored in `categoryGroups`) holds categories and a
  monthly amount. A **savings plan** is a goal that owns its monthly recurring item (a goal is a plan with
  no monthly amount). **Income lines** are the only income mechanism, each with optional auto-record on
  its pay day. Existing data is converted once, on both Firestore and the guest file, by app-start syncs
  that wait for a settled cache and never act on a partial one.
- **Consequences:** Fewer screens and words to learn, and the migrations are idempotent and testable.
  Legacy fields and documents stay readable (and allowed by the rules) for one release and are removed
  in 3.1. For 3.0 the migration syncs are not combined into one runner, and repositories do not share
  Firestore listeners yet; both are simpler once the migrations are deleted.
