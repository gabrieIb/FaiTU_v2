# 2025-11-09 – Firestore GA release

- Removed the last Google Sheets/App Script artifacts and stripped the HTTP sync layer from the Android app.
- Consolidated dependencies to Firebase Auth + Firestore; dropped WorkManager, OkHttp, and notification permissions.
- Reworked documentation (`README`, `docs/firebase-backend-setup.md`, `docs/data-schema.md`) for the Firestore-first architecture.
- Added build instructions, cleaned legacy CSV templates, and ensured `gradlew assembleDebug` succeeds.
