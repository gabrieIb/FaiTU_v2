# Fai tu! – Firestore Menu Planner

Jetpack Compose Android app backed by Firebase (Firestore + anonymous Auth) to plan the weekly menu together, keep ingredients in sync, and share a shopping list in real time. The Google Sheets + Apps Script stack is gone—Firestore is now the single source of truth.

## What you can do

- Propose lunches and dinners for specific days and slots.
- Attach ingredients, mark what still needs to be bought, and see the shopping list update instantly on both phones.
- Add ad-hoc shopping notes, mark items as purchased, or clear the menu entirely when you want to start fresh.
- Share access securely through a six-character invite code tied to your private household in Firestore.

## Repository layout

```
android-app/           # Jetpack Compose client, Firebase-backed
docs/                  # Firestore setup, data model, release notes
functions/             # Optional Cloud Functions to keep shopping entries in sync
sheets-template/       # Legacy CSV templates, retained for historical reference only
```

## Prerequisites

- Android Studio Jellyfish (or newer) with Android SDK 34.
- Firebase project with Firestore (Native mode) and Anonymous Authentication enabled.
- `google-services.json` downloaded for package `com.menumanager` and placed under `android-app/app/`.

See `docs/firebase-backend-setup.md` for the full Firebase walkthrough (Auth, Firestore rules, optional Cloud Functions).

## Build & run

```powershell
cd "android-app"
.\gradlew.bat assembleDebug
```

Install the generated APK from `android-app/app/build/outputs/apk/debug/` via `adb install`, Android Studio, or file transfer.

On first launch each device signs in anonymously, then you will:

1. Create a new household (generates the invite code) **or** join an existing one by entering the code shared from another device.
2. Start adding proposals and ingredients; everything syncs live through Firestore snapshot listeners.
3. Share the invite code with new devices anytime from the settings card.

## Data model

Firestore layout: `households/{householdId}` keeps metadata (`inviteCode`, members) and nests three collections per household—`proposals`, `ingredients`, `shopping`. See `docs/data-schema.md` for the exact document shapes and how the client derives the shopping list from ingredients.

## Documentation

- `docs/firebase-backend-setup.md` – configure Firebase services and recommended security rules.
- `docs/data-schema.md` – Firestore document schema and household structure.
- `docs/firestore-security-rules.md` – copy/paste rules to keep households private.
- `docs/session-log-2025-10-12.md` – latest development log.

## Release checklist

1. `.\gradlew.bat clean assembleRelease`
2. Run instrumentation tests if you have a device/emulator: `.\gradlew.bat connectedDebugAndroidTest`
3. Tag the commit: `git tag -a vX.Y.Z -m "Release notes"`
4. Publish the APK or App Bundle through the Play Console (or deliver directly), update release notes, and push the tag.

Happy planning! Firestore keeps both devices up to date—no manual refresh button required anymore.
