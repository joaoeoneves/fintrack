# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions are tagged `vX.Y`.

## [Unreleased]

## [2.3] - 2026-10-28

### Added
- The salary profile now has one income row per component: the salary is the net base, and the subsidy
  twelfths show up under Subsidies.
- Salary extras you define yourself (name, a fixed amount, a multiple of the base or a percentage of the
  annual gross, the months it is paid, deductions none / IRS / IRS and Social Security, and an optional
  fee). They replace the fixed flex, profit share and bonus fields; existing profiles are migrated
  automatically.
- Recurring items can be posted automatically on their due date.
- Optional "Record income on payday" (last working day of the month, Portuguese holidays respected), with a
  "Received" action on Plan and Home to record it early.
- When you add an expense, the category used last time for the same name is suggested.

### Changed
- The Plan tab is tidier: summary, pots, income, limits and groups (limits are listed inside their
  group), goals, recurring.
- IRS on the June and December lump sums is withheld at the base rate; the twelfths still use the subsidy
  rate.
- Salary-managed income lines are re-synced when the app starts.
- The limit and received sheets keep Save above the keyboard.
- E2E tests (two Maestro flows tagged `release`) now run only from the Release workflow on a tag, and a
  failure blocks publishing.
- The local and backup JSON schema is now 12. Firestore rules gained salary extras, `autoPost` on
  recurring items and `plan/automation`; deploy the rules before using the new version.

## [2.2] - 2026-10-27

### Added
- Savings goals linked to recurring items or a category, with an on-track status and a place in the
  yearly view.
- Insights splits fixed and variable spending and can group by category group.
- Settings: delete all data.

### Changed
- Simpler salary profile: one picker for both subsidy months, meal allowance on every working day, profit
  share over several months, and a prompt to remove income lines it already covers.
- Income is no longer double-counted between the salary profile and manual lines.
- The domain layer lives in a pure-Kotlin `:core:model` module.

## [2.1] - 2026-10-27

### Added
- Bottom navigation with Home, Activity, Plan and Insights.
- Activity: one list for expenses and income, with filters, search and undo on delete.
- Plan: income per working day, schedules and Portuguese holidays, days off per month with a Carnival
  shortcut, category groups, planned amounts from recurring items, pots for earmarked income, a salary
  profile (gross or net, subsidy spread, youth IRS, meal allowance, quarterly flexible benefits) and a
  yearly view.

### Changed
- The built-in Recurring category is retired in favour of a fixed spending view.

### Fixed
- Navigation back callbacks are guarded against double pops, night mode is applied from an effect,
  and the date-based resubscription on resume only happens when the date changed.
- The dashboard hero amount shrinks to fit, and tabs, the add pill and section actions have 48dp touch
  targets.
- Amounts are capped at 1,000,000,000.00 in the forms, local validation and Firestore rules; the rules
  also require `isRefund` to be a boolean and audit fields to be timestamps.

### Changed
- Test-only Firebase fields are kept out of release builds; dead use cases and a duplicate sort enum were
  removed.
- CI builds the unsigned release APK on every pull request, releases attach the R8 mapping file, and
  Dependabot watches Gradle and GitHub Actions dependencies.
- Baseline profiles regenerated for the current UI.

## [2.0] - 2026-10-27

### Added
- Complete visual redesign with a design system, a gold accent, a new app icon and a splash screen.
- Portuguese (pt-PT) localization with an in-app language picker.
- Guest mode: use the whole app without an account, with data in a local JSON file.
- Copy guest data into an account on sign-in (idempotent, rules-aware batching).
- Versioned JSON backup and restore.
- Monthly plan with a safe-to-spend card and daily allowance.
- Recurring transactions with an Upcoming card (confirm or skip).
- Custom categories with icon, colour and spending or saving kind.
- Refunds, search and sort on lists, and calendar-month period navigation.
- Settings stored in DataStore and synced across devices through Firestore.
- Baseline profiles, signed release builds published from version tags, and Maestro end-to-end flows on
  GitHub-hosted emulators.

### Changed
- Minimum Android version raised from 7.0 (SDK 24) to 8.0 (SDK 26).

### Security
- Firestore rules validate field allowlists, types, ranges and category references on every write.

## [1.0] - 2026-10-15

### Added
- Expense and income tracking with fixed categories, a dashboard with time-range filters, a category pie
  chart and net balance, and per-category monthly budgets.
- Expense CSV import and export.
- Google Sign-In with Firebase Auth, and Firestore storage with offline support.
- Light and dark themes, and a custom app icon.
- Firestore security rules restricting data to its owner.

[Unreleased]: https://github.com/joaoeoneves/fintrack/compare/v2.3...HEAD
[2.3]: https://github.com/joaoeoneves/fintrack/compare/v2.2...v2.3
[2.2]: https://github.com/joaoeoneves/fintrack/compare/v2.1...v2.2
[2.1]: https://github.com/joaoeoneves/fintrack/compare/v2.0...v2.1
[2.0]: https://github.com/joaoeoneves/fintrack/compare/v1.0...v2.0
[1.0]: https://github.com/joaoeoneves/fintrack/releases/tag/v1.0
