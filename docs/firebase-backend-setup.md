# Firebase Backend Setup

This guide replaces the old Google Sheets + Apps Script instructions. Follow the steps below to provision the Firebase backend that the Android app now uses.

## 1. Create or reuse a Firebase project

1. Visit [Firebase console](https://console.firebase.google.com/).
2. Create a new project or open the existing project dedicated to "Fai tu!".
3. Disable Google Analytics if you do not need it for this app (optional).

## 2. Register the Android app & download `google-services.json`

1. In **Project settings → Your apps**, add an Android app if it is not already registered.
2. Package name: `com.menumanager` (must match `android-app/app/src/main/AndroidManifest.xml`).
3. (Optional) App nickname: `Fai tu!`.
4. Download the generated `google-services.json` file and place it under `android-app/app/google-services.json`.
5. Commit the file only if you are comfortable storing Firebase config in source control; otherwise, share it privately with collaborators.

## 3. Enable Anonymous Authentication

1. Console → **Build → Authentication → Get started**.
2. Under the **Sign-in method** tab enable **Anonymous**.
3. No additional configuration is required: the app signs in anonymously on launch.

## 4. Create a Firestore database

1. Console → **Build → Firestore Database → Create database**.
2. Choose **Start in production mode** (recommended). If you pick test mode, remember to tighten rules later.
3. Select the region closest to your users (e.g. `europe-west1`).
4. Complete the wizard to create the database in Native mode.

## 5. Apply security rules

1. Open **Rules** in Firestore.
2. Copy the contents of `docs/firestore-security-rules.md` and paste them in the editor.
3. Publish. These rules require authenticated users and ensure they can only access their own household documents.

## 6. (Optional) Deploy Cloud Functions

The Android app already derives the shopping list client-side. If you prefer server-side regeneration or want a callable join endpoint, you can deploy the functions in `functions/src/index.ts`:

```bash
cd functions
npm install
npm run build
firebase use <your-project-id>
firebase deploy --only functions
```

Make sure you have the Firebase CLI installed and authenticated (`npm install -g firebase-tools`, `firebase login`).

## 7. Verify from the Android app

1. Build and install the app (`cd android-app && .\gradlew.bat assembleDebug`).
2. Launch on two devices/emulators.
3. On device A choose **Crea una nuova famiglia** and note the invite code.
4. On device B choose **Unisciti con codice invito** and enter the code.
5. Add meals and ingredients: both devices should see updates instantly.

Your Firebase backend is ready. No spreadsheet, Apps Script deployment, or manual tokens are needed anymore.
