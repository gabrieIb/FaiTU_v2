# Menu Planner App

Android app + Google Apps Script backend for two people to coordinate the weekly menu, list the needed ingredients, and share a shopping list. The app reads and writes a shared spreadsheet: every time one person edits a meal or toggles an ingredient, the other device sees the change after a quick refresh.

## What you can do

- Propose lunches and dinners for specific days.
- Attach the ingredients required for each proposal and flag what still needs to be bought.
- See a shared shopping list automatically generated from the “need to buy” flags and clear items as you grab them.
- Edit or delete proposals and ingredients directly from the phones—no more manual sheet edits.

## Repository layout

```
android-app/           # Jetpack Compose client; configuration is embedded in AppSecrets
apps-script/           # Google Apps Script backend (Code.gs)
docs/                  # Setup guides and data schema
sheets-template/       # CSV headers for the three tabs (MenuPlan, MenuIngredients, ShoppingList)
```

## Setup in five steps

1. **Spreadsheet** – Create a Google Sheet with three tabs: `MenuPlan`, `MenuIngredients`, `ShoppingList`. Import the CSV files from `sheets-template/` so the headers match exactly.
2. **Apps Script** – In Sheets choose *Extensions → Apps Script*, paste `apps-script/Code.gs`, save, set the script property `MENU_API_TOKEN`, and deploy it as a Web App (`Execute as: Me`, `Access: Anyone with the link`). See `docs/apps-script-setup.md` for screenshots.
3. **Embed the secrets** – Open `android-app/app/src/main/java/com/menumanager/AppSecrets.kt`, replace the placeholders with the Web App `/exec` URL and the token from step 2.
4. **Build the APK** – From `android-app/` run `.\gradlew.bat assembleDebug` (or use Android Studio). The debug APK appears under `android-app/app/build/outputs/apk/debug/`.
5. **Install & sync** – Install the APK on both phones (ADB, Android Studio, or file transfer). The app starts directly on the shared menu; tap **Aggiorna** to sync with the sheet.

## Daily workflow

- Add a meal proposal with date, slot (Pranzo/Cena), and optional notes.
- Add ingredients under each proposal; tap “Segna da comprare” to push them to the joint shopping list.
- While shopping, mark an ingredient “Segna come acquistato” to remove it from the list (the corresponding ingredient flag switches to *Ce l’abbiamo*).
- Use the refresh button whenever you want to pull the latest state from the sheet.

## Docs

- `docs/apps-script-setup.md` – deploy & link the Apps Script web app.
- `docs/data-schema.md` – reference for the three tabs and automatic shopping list regeneration.
- `docs/session-log-2025-10-12.md` – summary of this development session and implemented features.

No Google Cloud project, Firestore, or authentication SDK is required—just a shared spreadsheet, an Apps Script deployment, and the embedded token.
