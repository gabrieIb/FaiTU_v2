Purpose

Quick orientation for AI agents working on the collaborative weekly menu planner.

Architecture snapshot (Firebase)

- Android client (`android-app/`, Kotlin + Jetpack Compose) uses Firebase Auth (anonymous) and Firestore as the single source of truth. No HTTP layer, no Sheets SDK.
- Household-aware schema: `households/{householdId}` stores metadata plus three subcollections (`proposals`, `ingredients`, `shopping`). Details live in `docs/data-schema.md`.
- `HouseholdRepository` owns the authenticated household, persists the selection via DataStore, and exposes the invite code for the settings card.
- Menu data travels through `FirestoreMenuDataSource` → `MenuRepository` → `MenuViewModel`, where snapshot listeners emit app state. `MenuCache` keeps local derived state.
- Optional Cloud Functions (`functions/`) can mirror ingredient updates into the `shopping` collection, but the Android app is able to derive the combined list on its own.

Key workflows

- Build and test from PowerShell:
  - `cd android-app; .\gradlew.bat assembleDebug`
  - Instrumentation tests (device/emulator): `cd android-app; .\gradlew.bat connectedDebugAndroidTest`
- First run flow:
  - The app signs in anonymously as soon as `MenuManagerApp` starts.
  - `HouseholdSetupScreen` asks the user to create a household (generates a six-character invite code) or join an existing one.
  - Once `HouseholdState.Ready`, `MenuViewModel` begins streaming Firestore documents for that household.
- Backend checklist (see `docs/firebase-backend-setup.md`):
  - Create/register the Firebase project, enable Anonymous Auth, create Firestore in production mode.
  - Apply the rules from `docs/firestore-security-rules.md`.
  - Download the `google-services.json` for `com.menumanager` and place it under `android-app/app/`.

Coding patterns

- Compose-first UI: only viewmodels talk to repositories; composables never call Firebase directly.
- DataStore (`AppConfigRepository`, `HouseholdRepository`) is the single place for persisted configuration. New keys must be added there and wired through `ServiceLocator`.
- UUIDs and ISO-8601 timestamps come from utilities in `MenuRepository`. Firestore server timestamps are reserved for metadata (`createdAt` on households).
- Shopping list derivation: local UI combines manual entries stored in `shopping` with auto-generated entries from `ingredients` marked `needToBuy = true`, deduping by `ingredientId`.

Dependencies & auth

- Firebase BoM defines the Auth + Firestore versions; the Google Services Gradle plugin is already applied in `android-app/app/build.gradle.kts`.
- `google-services.json` must match the `applicationId`. Replace it per-environment rather than editing the manifest.
- If Functions are deployed, keep their Node dependencies locked through `functions/package.json`.

Testing & verification

- Unit tests live in `android-app/app/src/test/` (use coroutine TestDispatchers). Instrumentation tests belong in `android-app/app/src/androidTest/`.
- Validate Firestore rules either with the Emulator Suite or with two physical devices signed in anonymously to different households.
- Manual sanity checks: create a household, share the invite code, add meals/ingredients, and confirm real-time updates on both devices.

Common pitfalls

- Mismatched package ID vs Firebase app results in `google-services.json` runtime crashes.
- OneDrive paths occasionally confuse the Google Services Gradle plugin cache; a clean build resolves most issues.
- Keep Firestore indexes small: avoid `orderBy` on fields without indexes and update the schema doc whenever you change queries.

Safe extension guidelines

- New screens belong under `android-app/app/src/main/java/com/menumanager/ui/` and should obtain their data through existing viewmodels or new ones registered in the `ServiceLocator`.
- When introducing new Firestore documents or fields, update `docs/data-schema.md`, add mapping helpers in `FirestoreMenuDataSource`, and backfill existing data if required.
- Before shipping backend changes, run through `docs/firebase-backend-setup.md` to ensure collaborators can reproduce the setup.

Legacy note

- The Apps Script + Google Sheets backend is archived (`apps-script/` and `docs/apps-script-setup.md`). Leave it untouched unless you need to reference the old implementation.
