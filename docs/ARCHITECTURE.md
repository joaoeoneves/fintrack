# Architecture

FinTrack is an Android app (`:app`, package `com.joaoeoneves.fintrack`) with a pure-Kotlin `:core:model`
module (see Modules below) plus a `:baselineprofile` module that generates the baseline profile. It is MVVM on Jetpack Compose, with Hilt
for dependency injection and Coroutines/`Flow` for async work.

## Modules

| Module | Contents |
|---|---|
| `:core:model` | Pure Kotlin/JVM (no Android): `domain/model`, `domain/repository` (except `AuthRepository`/`SessionRepository`, which take an Android `Context` for sign-in and stay in `:app`), `domain/usecase` and the `@IoDispatcher` qualifier. Depends only on coroutines and `javax.inject`; packages are unchanged. Its unit tests run on the plain JVM (`:core:model:test`). |
| `:app` | Everything else: UI, data, DI, the Hilt application. Depends on `:core:model`. |
| `:baselineprofile` | Baseline profile generator, targets `:app`. |

ktlint and detekt run per module and share `app/config/detekt/detekt.yml`; the root `ktlintCheck`/`detekt` tasks aggregate them.

## Layers and packages

```mermaid
flowchart TB
    subgraph ui["ui/ (Compose)"]
        S[Screens + composables] --> VM[ViewModels<br/>StateFlow UI state]
    end
    subgraph domain["domain/"]
        UC[use cases]
        M[models]
        RI[repository interfaces]
    end
    subgraph data["data/"]
        SS["session/<br/>Session* repositories"]
        FS[Firestore repositories]
        LO["local/<br/>JSON store + repositories"]
        PR[preferences/ DataStore]
        BK[backup/ and migration/]
    end
    VM --> UC --> RI
    VM --> RI
    SS -. implements .-> RI
    SS --> FS
    SS --> LO
```

| Package | Responsibility |
|---|---|
| `ui/` | Compose screens per feature (`dashboard`, `activity` (+ `analytics/` summary header), `expense`, `income`, `plan` (envelopes, income, salary profile and `salary/setup` wizard), `savings`, `recurring`, `settings`, `auth`, `backup`, `migration`, `importexport`), shared pieces (`common`, incl. the account avatar), `navigation` (central graph with type-safe routes, `NavMotion` transitions), `theme` |
| `ui/designsystem/` | Reusable building blocks: buttons, text fields, amount input, animated amount text, progress bar and ring, skeletons, haptics, segmented tabs, settings rows, transaction row, dialogs, snackbar, empty state, large-title scaffold |
| `domain/` | Models (`Expense`, `Income`, `Category`, `RecurringItem`, `MonthlyPlanSummary`, `Period`, ...), repository interfaces, and use cases such as `ObservePeriodOverview`, `ObserveUpcomingRecurring`, `HandleRecurringOccurrence` |
| `data/` | Firestore repositories, `local/` guest store, `session/` switching, `backup/` (incl. signed-in restore and `RestoreGate`), `migration/` (guest → account copy and the 3.0 data migrations), `salary/`, `automation/`, `preferences/`, `csv/` |
| `di/` | Hilt modules binding interfaces to implementations, dispatchers, the guest data file and the clock |

State rules: UI state is a `StateFlow` in the ViewModel, collected with `collectAsStateWithLifecycle`;
`remember`/`mutableStateOf` is used only for local, ephemeral UI state. Navigation arguments travel
through routes, not shared state.

## Session-switched repositories

Every repository interface in `domain/repository/` has up to three implementations:

- a Firestore-backed one (`data/Firestore*Repository`),
- a local one for guest mode (`data/local/Local*Repository`),
- an in-memory fake used only by tests.

Hilt binds each interface to a `Session*Repository` (`data/session/`). It holds a `SessionSwitch`, which
observes the current `Session` (loading, guest, signed out, signed in) and picks the implementation:

```mermaid
flowchart LR
    VM[ViewModel] --> SR[SessionExpenseRepository]
    SR --> SW{SessionSwitch<br/>DataSource}
    SW -->|Remote uid| FR[FirestoreExpenseRepository]
    SW -->|Local| LR[LocalExpenseRepository]
    FR --> F[(Firestore)]
    LR --> J[(guest-data.json)]
```

- Flows re-subscribe (`flatMapLatest`) when the data source changes, for example after sign-in.
- One-shot calls wait for the session to resolve before choosing.
- The Firestore repository is injected as `dagger.Lazy`, so a guest never instantiates Firestore.
- If a local flow fails (for example an unreadable file) the failure is logged and an empty result is
  emitted, mirroring how the Firestore repositories degrade.

## Firestore data model

All data lives under `users/{uid}/`. Money is stored as integer minor units (`amountCents`), never floats.

| Path | Fields (summary) |
|---|---|
| `expenses/{id}` | `name`, `amountCents` (> 0), `category` (category id), `isRefund?`, `date`, `note?`, `createdAt`, `updatedAt` |
| `income/{id}` | `source` (free text), `amountCents`, `date`, `note?`, timestamps |
| `budgets/{categoryId}` | `limitCents` (the document id is the category id), `skipMonths?` (months 1..12 the limit does not apply to) |
| `plannedIncome/{lineId}` | `label` (1..50), `amountCents` (the rate for per-working-day lines), `order`, `basis?` (`FIXED`/`PER_WORKING_DAY`), `frequency?` (`MONTHLY`/`YEARLY`/`ONCE`), `monthOfYear?` (yearly only), `yearMonth?` (`yyyy-MM`, one-off only), `skipMonths?` (list of months 1..12 a monthly line is not paid in), `earmarkType?` + `earmarkId?` (legacy 2.x pot earmark, read only by the 3.0 migration), `autoRecord?` (record the line on its pay day), `payDay?` 1..31 (fixed pay day; absent = last working day), `managedKey?` (one of the `profile_*` keys: the line is generated from the salary profile, read-only except label/earmark); absent fields mean fixed/monthly; at most 20 lines (enforced client-side, rules cannot count documents) |
| `plan/salaryProfile` | singleton: `inputMode` (`GROSS`/`NET`), rates as integer ppm (`subsidySpreadPpm` 0/500000/1000000, `irsBasePpm`, `irsSubsidiesPpm?`, `ssPpm`, `irsJovemExemptPpm`), amounts in cents (`baseGrossCents?`, `netMonthlyCents?`, `netHolidayLumpCents?`, `netChristmasLumpCents?`, `mealRatePerDayCents?`), months 1..12 (`holidaySubsidyMonth`, `christmasSubsidyMonth`), `mealSkipMonths`, `extras` (list of up to 10 `{id, name, amountType` FIXED_CENTS/MULTIPLE_OF_BASE_PPM/PERCENT_OF_ANNUAL_GROSS_PPM`, amount, months, deductions?, feePpm?, amountScope?` TOTAL/PER_PAYMENT, omitted = TOTAL`}`; the rules accept only those two scopes; absent = legacy `flex*`/`profitShare*`/`bonus*` fields migrated on read), `updatedAt`; saving it upserts the managed `plannedIncome` lines, deleting it removes them |
| `plan/automation` | singleton: `lineThrough?` (map lineId → `yyyy-MM`, the last month each auto-record line was recorded, at most 64 entries), legacy `paydayEnabled?`/`paydayThrough?` (read once by the 3.0 migration), `updatedAt` |
| `categoryGroups/{id}` | **envelopes**: `name` (1..50), `order`, `amountCents?` (a single monthly amount; absent = the sum of its categories' limits, or no budget when there are none), `fundedBy?` (an income line id), `updatedAt`; categories point at one with the optional `groupId` (a dangling id means no envelope); saving-kind categories are never part of an envelope |
| `goals/{id}` | **savings plans**: `name` (1..50), `targetCents` (0 = no target), `recurringId?` (the hidden monthly SAVING recurring item the plan owns), `targetType` (`ANNUAL`/`TOTAL`), `startDate`, `targetDate?` (not before `startDate`), `initialCents?` >= 0, `linkedRecurringIds?` (list, at most 20), `linkedCategoryId?` (shape-checked only), `archived`, `order`, `updatedAt`; progress is computed from the expenses of the linked recurring items (id prefix `rec_<itemId>_`) and category; a plan's own item is hidden from Bills |
| `monthSettings/{yyyy-MM}` | `workingDays?` 0..23 (manual working-days count replacing the Portuguese-holiday calendar count for that month), `daysOff?` 0..23 (vacation/days off taken; effective working days = override, else calendar count minus days off); the document is deleted when neither is set; `updatedAt` |
| `recurring/{itemId}` | `type` (`EXPENSE`; legacy `INCOME` items are converted to income lines by the 3.0 migration and deleted), `name`, `amountCents`, `category?`, `note?`, `frequency` (`MONTHLY`/`YEARLY`), `dayOfMonth` 1..31, `monthOfYear?`, `startDate`, `endDate?`, `handledThrough?`, `active`, `autoPost?`, `updatedAt` (dates as `yyyy-MM-dd`) |
| `categories/{id}` | `name?`, `iconKey`, `colorKey`, `kind` (`SPENDING`/`SAVING`), `order`, `archived`, `builtIn`, timestamps. User categories have generated ids; a built-in's override document reuses the built-in id with `name` null meaning the localized default |
| `settings/preferences` | `theme` (`SYSTEM`/`LIGHT`/`DARK`), `currency` (`USD`/`EUR`/`GBP`/`JPY`/`CAD`/`AUD`), `language` (`SYSTEM`/`ENGLISH`/`PORTUGUESE`), `updatedAt` |

Built-in category ids are `TRANSFER`, `INVESTMENTS`, `SHOPPING`, `FOOD` and `RECURRING`.

## Security rules

[`firestore.rules`](../firestore.rules) follows three principles:

1. **Owner-only.** Every collection is reachable only when `request.auth.uid == userId`.
2. **Allowlisted, typed fields.** Each create/update must pass a validator that uses
   `data.keys().hasOnly([...])` and `hasAll([...])` plus type and range checks (positive integer amounts
   up to 1,000,000,000.00, string length limits, date shapes, enum values). A modified client cannot add
   unknown fields or write a negative amount.
3. **Referential checks.** An expense's category must be a built-in id or a category document of the same
   user (`existsAfter`, so one batch can create a category and use it). Budgets and recurring expenses
   follow the same rule.

The client mirrors these limits (`FieldLimits`, local record validation) so invalid input is rejected
before a write, and the rules remain the authority. Adding an enum value in the app requires deploying
the rules first.

## Offline-first writes

Firestore's persistent local cache is the only persistence layer for signed-in users (no Room). Write
operations such as add/update/delete are deliberately **not awaited**: the write lands in the local cache
immediately and syncs when the device is online, whereas awaiting would hang until server
acknowledgement while offline. Reads are snapshot listeners wrapped in `callbackFlow`.

## Guest mode: the local JSON store

`LocalDataStore` holds the guest data set (`LocalDataDocument`: `schemaVersion`, `revision`, and lists of
expenses, income, budgets, planned income, categories, recurring items, month settings, category groups, salary profile, goals, automation settings; ISO-8601 dates) in
`guest-data.json` under `filesDir`.

- Loaded lazily, kept in memory, rewritten on every change.
- **Atomic writes**: temp file, fsync, rename. New state is published to observers only after the write
  succeeded, so no one sees data that is not on disk. A mutex serialises writers.
- **Versioning**: `schemaVersion` is currently 12. A corrupt file, or one with a newer schema, is moved to
  `guest-data.corrupt-<millis>.json` (never deleted) and the store starts empty.
- After a migration to an account and a confirmed "Remove from this device", one safety copy remains as
  `guest-data.migrated.json`, overwritten next time.

## Backup format

A backup is a JSON document `{ "format": "fintrack-backup", "schemaVersion": 16, "exportedAt",
"appVersion", "preferences", ...records }` using the same record shapes as the guest store. Decoding:

- a missing or invalid version is rejected; a version newer than the app supports is refused;
- older versions are upgraded in memory to the current one;
- records that fail validation are skipped and reported to the user on restore.

Backups can be exported and restored signed in or as a guest. A signed-in restore
(`AccountBackupRestorer`) first reads every account collection from the server (nothing is written if
that fails), then upserts the backup's records in batches of at most 450 in dependency order, and finally
deletes the account documents that are not in the file. Ids are kept, so a retry after a partial failure
converges without duplicates. `RestoreGate` pauses the app-start syncs during any restore; when it
releases, they run on the restored data, which is also how a pre-3.0 backup gets converted.

## Guest to account migration

When a guest signs in and there is local data, the app offers to copy it into the account
(`data/migration/`).

```mermaid
sequenceDiagram
    participant U as User
    participant P as MigrationPlanner
    participant M as FirestoreAccountMigrator
    participant F as Firestore
    U->>P: choose "Copy to account"
    P->>F: read existing ids and categories
    P-->>M: plan (records not yet in the account, remapped categories)
    loop each batch of at most 400 writes
        M->>F: batch.set(users/uid/..., id preserved) and await commit
    end
    M-->>U: progress, then offer to remove local data
```

- **Idempotent**: ids are preserved and records already present in the account are left out of the plan,
  so an interrupted copy can simply be retried; earlier committed batches are skipped the second time.
- **Category merge**: categories with the same name are merged rather than duplicated, and records are
  remapped to the account's category ids. Records that cannot be migrated are counted as skipped.
- **Rules-aware batching**: a batch has at most 400 writes (the hard limit is 500) and references at most
  15 distinct custom categories, because each custom-category reference costs a rules lookup and a batched
  write may do at most 20 document-access calls. Categories are committed first so later batches find them.
- Unlike normal writes, the migrator awaits each commit, since the user must know the data reached the
  server before offering to delete it locally.

## Recurring transactions

A `RecurringItem` is a template (monthly or yearly, a billing day clamped to the month length, start and
optional end date, pause flag). A `handledThrough` cursor records the last confirmed or skipped
occurrence; `ObserveUpcomingRecurring` derives the due and soon-due occurrences from the cursor.

Confirming an occurrence creates the expense or income with a **deterministic id**,
`rec_<itemId>_<yyyyMMdd>`, and advances the cursor in the same atomic write. Because the id is a pure
function of the item and date, a double tap or a second device writes the same document instead of a
duplicate; `HandleRecurringOccurrence` also re-reads the latest item and treats an already-handled
occurrence as a successful no-op. Skipping only advances the cursor.

Items with `autoPost` set go through the same path without a tap: `AutoPostRecurring` confirms every
occurrence that is due, so the deterministic id keeps it safe if two devices do it at once.

## Monthly plan

The plan is global (one set of income lines) and built on **envelopes**. For a month:

- `expectedIncome` = sum of the income lines active that month; `actualIncome` = income dated in it;
  `incomeBasis` = max(expected, actual).
- Each envelope commits max(its monthly amount, what was spent in its categories). Its amount is either a
  single `amountCents` or the sum of its categories' limits (`budgets`, with `skipMonths`). A "no budget"
  envelope only tracks spending.
- Bills (active recurring expenses) and savings (saving-kind categories: max(planned, actual)) are
  committed separately; savings categories are never part of an envelope.
- **Free** = `incomeBasis - committed`; negative means the plan is overrun. The daily allowance is
  `free / days left` (today included), shown only for the current month while something is left.

**Savings plans** (`goalProgressOf`) count the expenses of the plan's own recurring item (id prefix
`rec_<itemId>_`), its linked items and category, against a yearly or total target (`effectiveTargetCents`
falls back to the yearly monthly amount when there is no target).

## App-start syncs

`AppStartSyncs` starts these singletons when the app starts; each follows the session, waits for a stable
session, runs off the main thread and attempts the same write only once:

- `PreferencesSync` keeps theme, currency and language in step with `settings/preferences`.
- `ManagedIncomeSync` (`data/salary/`) re-generates the income lines managed by the salary profile.
- `EnvelopeMigrationSync`, `SavingsPlanMigrationSync` and `IncomeMigrationSync` (`data/migration/`)
  convert 2.x data once (groups/limits/earmarks → envelopes, investment recurring items and goals →
  savings plans, recurring income and the payday switch → auto-record income lines). They wait 2 s for
  the cache to settle and never act on a partial cache; legacy documents are left in place until 3.1.
- `AutomationSync` (`data/automation/`) runs `AutoPostRecurring` (bills and savings plans) and
  `RecordPayday` (each auto-record income line on its pay day, with per-line `lineThrough` cursors and
  deterministic `pay_<lineId>_<yyyyMM>` ids) whenever the session, its data or the foreground state
  changes, which is how a new day is picked up without a background job.

All of them are paused by `RestoreGate` while a backup is being restored.

## Localization

English is the default resource set; Portuguese (pt-PT) lives in `values-pt-rPT`. The in-app language
picker (System, English, Portuguese) uses AppCompat per-app locales, which is why `MainActivity` extends
`AppCompatActivity`; the locale list is generated (`generateLocaleConfig`). Built-in category names are
localized and reserved in every language so users cannot create a clashing name. Language, theme and
currency are kept in DataStore and, when signed in, synced through `settings/preferences`.

## Theming and design system

`FinTrackTheme` provides colours, typography (bundled Inter with tabular figures), shapes, dimensions and
motion tokens. The look is dark-first with a gold accent, hairline dividers and colour reserved for
meaning (gains, overspending, category data). Screens compose the in-house components from
`ui/designsystem/` on top of Material 3. Motion uses shared tokens (`Motion`, `motionSpec`,
`motionAnimateItem` in `ui/theme/`) and respects `LocalReducedMotion`, which combines the system animator
scale with the device-only "Reduce animations" preference.

## Performance

R8 is enabled for release builds. A baseline profile, generated by the `:baselineprofile` module on a CI
emulator (guest mode, so no account is needed) and committed under `app/src/main/generated`, speeds up
startup and the dashboard.
