# Fai tu! - Firestore Menu Planner

Jetpack Compose Android app backed by Firebase (Firestore plus Anonymous Auth) to plan the family menu, sync ingredients, and share a live shopping list. The Google Sheets and Apps Script backend is retired: Firestore is the single source of truth.

## Highlights

- Real-time proposals, ingredients, and shopping entries scoped to a private household.
- Anonymous Auth with invite codes so two devices can collaborate securely.
- Compose-only UI backed by `MenuViewModel` and Flow streams from Firestore.
- Optional Cloud Functions keep the `shopping` collection aligned with ingredients, although the app can derive the combined list on-device.

## Repository layout

```
android-app/           # Jetpack Compose client, Firebase-backed
docs/                  # Firestore setup, schema, release notes
functions/             # Optional Cloud Functions for shopping sync
sheets-template/       # Legacy CSV templates kept for history only
```

## Prerequisites

- Android Studio Jellyfish (or newer) with Android SDK 34.
- Firebase project with Firestore (production mode) and Anonymous Authentication enabled.
- `google-services.json` for package `com.menumanager` copied to `android-app/app/`.

See `docs/firebase-backend-setup.md` for the step-by-step backend checklist.

## Quickstart

1. **Configure Firebase**
   - Follow `docs/firebase-backend-setup.md` to create the project, enable Anonymous Auth, create Firestore, and apply the rules from `docs/firestore-security-rules.md`.
   - Place the downloaded `google-services.json` in `android-app/app/`.
2. **Build the app**
   ```powershell
   cd android-app
   .\gradlew.bat assembleDebug
   ```
   Install the APK from `android-app/app/build/outputs/apk/debug/` via Android Studio or `adb install`.
3. **Run the onboarding**
   - Launch on device A, create a household, and note the six-character invite code.
   - Launch on device B, choose "Join with invite code," and enter the code.
   - Add meals and ingredients; both devices update instantly through Firestore snapshot listeners.
4. **Tests**
   - Unit tests: `cd android-app; .\gradlew.bat testDebugUnitTest`
   - Instrumentation tests (device/emulator): `cd android-app; .\gradlew.bat connectedDebugAndroidTest`

## Data model

Firestore stores one document per household under `households/{householdId}` with metadata (`inviteCode`, `members`, `createdAt`). Each household owns three subcollections:

- `proposals`: menu ideas linked to meal slots.
- `ingredients`: items tied to proposals with `needToBuy` flags.
- `shopping`: manual or function-generated shopping entries.

The Android app listens to all three collections and builds derived UI models (see `docs/data-schema.md` for field-by-field details).

## Documentation

- `docs/firebase-backend-setup.md` - complete Firebase configuration guide.
- `docs/data-schema.md` - Firestore schema and derived shopping logic.
- `docs/firestore-security-rules.md` - copy/paste security rules.
- `docs/firebase-migration-guide.md` - history and checklist of the move away from Sheets.
- `docs/session-log-2025-10-12.md` - latest development log.
- `docs/apps-script-setup.md` - archived instructions for the previous backend.

## Release checklist

1. `cd android-app && .\gradlew.bat clean assembleRelease`
2. Run `.\gradlew.bat connectedDebugAndroidTest` on a device or emulator.
3. Tag the commit (`git tag -a vX.Y.Z -m "Release notes"`).
4. Submit the artifact to the Play Console (or distribute directly) and push the tag.

Happy planning! Firestore keeps both devices in sync without any manual refresh.
