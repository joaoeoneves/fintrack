# Security policy

## Supported versions

Only the latest [release](https://github.com/joaoeoneves/fintrack/releases/latest) receives fixes.

## Reporting a vulnerability

Please do not open a public issue for security problems. Report them privately through GitHub's
[private vulnerability reporting](https://github.com/joaoeoneves/fintrack/security/advisories/new) for this
repository, with steps to reproduce and the impact you see. You can expect an initial reply within a
week.

## Scope

In scope:

- [`firestore.rules`](firestore.rules): any way to read, write or delete another user's data, or to
  write data that bypasses the validation (field allowlists, types, ranges, category references).
- Handling of personal financial data on the device: guest data file, backup export and restore, CSV
  import.
- The release pipeline and signed APKs published on GitHub.

Out of scope: vulnerabilities in third-party services (Firebase, Google Sign-In) themselves, and issues
that require a rooted device or physical access to an unlocked phone.

## Data and secrets

Each signed-in user's data lives under `users/{uid}/` in Firestore and is accessible only to that user.
Guest data stays on the device. The repository must never contain `google-services.json`, signing keys or
`local.properties`; if you find one, please report it as above.
