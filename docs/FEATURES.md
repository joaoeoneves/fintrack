# FinTrack features

Everything the app does, screen by screen. The [README](../README.md) has the short version.

## Tabs
- Home, Activity, Income and Spending, plus an avatar with your initial that opens Account & settings.
  Every tab has the same Month | Year switch (calendar year).
- **Home**: a **Get started** checklist (hidden when done; it ticks a step once the server has confirmed it),
  a **To review** card (records posted automatically and "Make it recurring?" suggestions), an income card
  (received of expected), a spending card, an *Invested* row with the share of what you received ("you invested 12% of what you received")
  that opens the investments page, quick add and an informative list of what is coming up.
  An **Awaiting refund** card lists the expenses marked *Will be refunded* that are still owed, with the
  total owed, how much of each is left and a *Record refund* action that opens a prefilled refund.
- **Activity**: the list of expenses and income, with search, type, category, group and income source
  filters (search also matches the note and the amount, e.g. "12,50 €"), sorting, delete by swiping or
  from the edit screen, and multi-select by long press to change the category, toggle the meal card or
  delete several expenses at once (all with undo). Totals and breakdowns live
  in Spending and Income.
- **Income**: received versus expected (the whole period, January to December in the Year view) with what is still to receive, income lines, days off by type
  (vacation and other; one month or several at once; the working-day count follows from the
  calendar, holidays and days off), and history. Income counts by the date it was
  received.
- **Spending**: spent, fixed versus variable, evolution, and spending by category or by group (a name
  plus its categories) with a Category | Group switch; a category spending more than its recent average is marked *above
  average*. Recurring bills and investments are managed here,
  with their yearly cost (monthly and yearly items apart) and the month each one ends.
- Tapping a bar in a year chart shows that month's figures and opens the month.
- Detail pages per category and per income source. For the net salary source it shows what was received
  at the end of a month against what was spent in the next one.

## Tracking
- Expenses (name, amount, category, date, note) and income (source, amount, date, note). Refunds
  subtract from their category and can be linked to the original expense, which then shows the refunded
  share and the real spend. An expense can be duplicated, and a new one can be split across categories
  (*More options*); opening any part of a split edits all its parts together. When you add an expense, the category you used last time for that name
  is suggested.
- Categories: built-ins plus your own (new users also start with Home, Transport, Health and Leisure) (name, icon, colour, spending or saving), with rename, reorder,
  archive and delete. *How it works* in Settings explains categories, groups, recurring items and To review.
- Income lines that change over time keep their history: a new amount applies
  from the month you choose (*Valid from*) and earlier months keep the old one.
- CSV export of expenses and income (one file with a type column).

## Income lines
- The salary profile (optional) and your own lines, one row per component. Salary set-up is three steps
  (gross or net, subsidies, meal allowance); the full form under Advanced covers youth IRS, deductions
  and extras (a fixed amount, a multiple of the base or a share of the annual gross, paid in the months
  you choose).
- Any line can be recorded automatically on its pay day (last working day, with Portuguese holidays, or
  a fixed day). Income can be per working day, with days off.
- The salary profile shows the vacation days left this year, and an optional annual raise in February
  makes a new salary apply from February (January keeps the previous one).

## Bills and investments
- Monthly or yearly recurring expenses (rent, subscriptions) and recurring investments, with start and
  end dates and pause/resume.
- Every recurring item is recorded automatically on its due date and shows up in *To review*, where you
  can check or change it. Recording twice (for example from a second device) never creates a duplicate.
  The upcoming list on Home is informative.

## Guest mode and backup
- "Continue as guest" uses the whole app with data kept in a single JSON file on the device; no Firebase
  is involved.
- Signing in later offers to copy guest data into the account, then to remove it from the device.
- Versioned JSON backup and restore (for example to move to another phone), for guests and signed-in
  accounts. Restoring into an account offers to export a backup first. A "delete all data" action is
  also available.

## Localization and appearance
- English and Portuguese (pt-PT), switchable in-app; follows the system language by default.
- Dark (default), light or system theme. The *Add* button hides while you scroll down and comes back
  when you scroll up. Motion (smooth transitions, predictive back, counting amounts, skeleton
  loading and haptics) follows the system's animation setting.
- Amounts are in euros.

## Privacy
- Optional app lock (fingerprint, face or screen lock) when the app opens and after 1 minute away; while
  it is on, the app preview in recent apps is hidden.
- Sign-in is Google via Credential Manager; data is stored under your own user id and Firestore security
  rules restrict every document to its owner (see [SECURITY.md](../SECURITY.md)).
- Guest data never leaves the device unless you export a backup.

