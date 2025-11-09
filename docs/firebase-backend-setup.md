# Firebase Backend Setup

This guide replaces the old Google Sheets and Apps Script instructions. Follow the steps below to provision the Firebase backend used by the Android app.

## 1. Create or reuse a Firebase project

1. Visit https://console.firebase.google.com/.
2. Create a new project or open the existing "Fai tu!" project.
3. Disable Google Analytics if you do not need it for this app (optional).

## 2. Register the Android app and download `google-services.json`

1. Project settings > Your apps > Android.
2. Package name: `com.menumanager` (must match `android-app/app/src/main/AndroidManifest.xml`).
3. Optional nickname: `Fai tu!`.
4. Download the generated `google-services.json` file and place it under `android-app/app/google-services.json`.
5. Keep the file out of source control if you prefer to share it privately with collaborators.

## 3. Enable Anonymous Authentication

1. Console > Build > Authentication.
2. Click Get started, open the Sign-in method tab, and enable Anonymous.
3. No further configuration is required; the app signs in anonymously at launch.

## 4. Create a Firestore database

1. Console > Build > Firestore Database > Create database.
2. Choose Start in production mode (preferred). If you temporarily pick test mode, tighten the rules before release.
3. Select the region nearest to your users (for example `europe-west1`).
4. Finish the wizard to create a Firestore database in Native mode.

## 5. Apply security rules

1. Open the Rules tab in Firestore.
2. Copy the contents of `docs/firestore-security-rules.md` into the editor.
3. Publish the rules. They require authenticated users and limit reads/writes to their own households.

## 6. (Optional) Deploy Cloud Functions

The Android client already derives shopping entries locally. If you prefer server-side mirroring or want callable helpers, deploy the functions in `functions/`:

```bash
cd functions
npm install
npm run build
firebase login
firebase use <your-project-id>
firebase deploy --only functions
```

Keep the Firebase CLI (`npm install -g firebase-tools`) up to date. These functions expect the same Firestore schema documented in `docs/data-schema.md`.

## 7. Verify from the Android app

1. Build and install the app: `cd android-app && .\gradlew.bat assembleDebug`.
2. Launch on two devices or emulators.
3. Device A: choose "Create a new household" and write down the invite code.
4. Device B: choose "Join with invite code" and enter the code from device A.
5. Add meals and ingredients. Both devices should update immediately via Firestore snapshot listeners.

Your Firebase backend is ready. No spreadsheet, Apps Script deployment, or manual tokens are required anymore.
