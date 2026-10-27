# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions are tagged `vX.Y`.

## [Unreleased]

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

[Unreleased]: https://github.com/joaoeoneves/fintrack/compare/v2.0...HEAD
[2.0]: https://github.com/joaoeoneves/fintrack/compare/v1.0...v2.0
[1.0]: https://github.com/joaoeoneves/fintrack/releases/tag/v1.0
