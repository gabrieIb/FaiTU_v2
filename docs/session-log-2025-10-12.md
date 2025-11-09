# 2025-11-09 - Firestore GA release

- Removed the last Google Sheets / Apps Script artifacts and stripped the HTTP sync layer from the Android app.
- Consolidated dependencies to Firebase Auth + Firestore; dropped WorkManager, OkHttp, and notification permissions.
- Reworked documentation (`README`, `docs/firebase-backend-setup.md`, `docs/data-schema.md`, `docs/firestore-security-rules.md`).
- Added build instructions, cleaned legacy CSV templates, and ensured `gradlew assembleDebug` succeeds.
- 2025-11-09 follow-up: refreshed all docs again after Firebase launch (Copilot instructions, README, backend guide, migration guide, security rules, session log).
