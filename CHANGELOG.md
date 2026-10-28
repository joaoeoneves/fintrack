# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions are tagged `vX.Y`.

## [Unreleased]

## [3.0] - 2026-10-28

"Organise": fewer concepts, a clear place for everything, and a smoother app.

### Added
- **Envelopes** replace groups, category limits and pots. An envelope has a name, its categories and a
  monthly amount (a single amount or the sum of per-category limits). An envelope can also be "no budget",
  which only tracks what was spent this month. Plan shows envelope cards with progress and what is left
  ("Free").
- **Savings tab** with savings plans. A plan has a monthly amount, a day, a category and an optional target
  (per year or total, with an optional date). Its monthly contribution is recorded automatically. A goal is
  a savings plan without a monthly amount. The tab also shows the amount saved this year and the savings
  rate.
- **Income in one place.** Each income line can be recorded automatically on its pay day (last working day
  or a fixed day). Recurring income becomes an income line, and a manual line that looks like the salary
  shows a warning.
- **Activity summary**: period tabs (1M/3M/6M/1Y), total spent, fixed vs variable, spending by envelope
  with filter chips, and a breakdown by category or envelope.
- **Get started** checklist on Home (salary, envelopes, bills, savings plan, pay day, first expense). Each
  step links to its screen. The checklist hides when done or dismissed.
- **Salary set-up** in three steps (gross or net, subsidies, meal allowance) with a summary. The full
  profile is still under "Advanced".
- **Restore a JSON backup into a signed-in account.** A warning offers to export a backup first. The
  restore replaces the account data, and a restore that stops part-way can be retried safely.
- **Reduce animations** option (System/On/Off), stored on the device.
- Motion: smoother screen and tab transitions, predictive back, counting amounts, progress bars and rings
  that draw in, animated lists, haptics, a small celebration when a goal is reached, and skeleton loading
  instead of spinners.

### Changed
- Tabs are now Home, Activity, Plan and Savings. An avatar with your initial opens "Account & settings",
  which keeps only account, preferences, data and about.
- Bills (recurring items) are expenses only. Savings categories are no longer part of envelopes.
- Plan has a "Set up" section: Income, Envelopes, Bills and Categories.
- First start migrates existing data automatically: groups and limits become envelopes, investment
  recurring items and goals become savings plans, and recurring income becomes income lines. Old documents
  are kept read-only until 3.1.
- The local and backup JSON schema is now 16. Older backups still restore and are converted.
- A smaller APK: only the English and Portuguese resources are bundled.

### Removed
- The Insights tab (its charts are now in Activity), the groups sheet, the earmark picker, the goals card
  on Plan and the payday switch in Settings.

## [2.6] - 2026-10-28

### Fixed
- Empty amount fields show a single grey 0,00 instead of two overlapping zeros.
- Salary extras show their name in full, with the amount on a second line, and long values no longer
  squeeze row titles.

## [2.5] - 2026-10-28

### Added
- A salary extra paid in several months can now be a yearly total (split over the months) or an amount per
  payment (paid in each month).

### Changed
- Amounts are typed as digits and the app places the decimal separator (1250 → 12,50 €). Separators follow
  the app language, so Portuguese never shows a dot.
- Salary lines and income recorded with "Received" or on payday use the app language. Existing English
  names are fixed automatically.

## [2.4] - 2026-10-28

### Added
- Delete an expense or income from its edit screen, with Undo. Removing a goal, an income line or a limit
  now asks first.
- Mark income as received with a small check next to the amount. It turns green, and tapping it again
  undoes it.

### Changed
- The "Earmark for" picker separates groups from categories.
- Clearer wording: "No limit", "Recurring", no duplicate plan title, readable CSV errors.
- Settings: theme, language and currency as single rows under Preferences.
- Tapping a category in Insights opens its transactions. Suggested groups use your own active categories,
  and group names must be unique.

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
