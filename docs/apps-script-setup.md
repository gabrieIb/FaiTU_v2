# Apps Script Backend Setup

These steps deploy the lightweight REST backend that the Android app calls. The script now exposes batched proposal saves, manual shopping note helpers, the usual CRUD for proposals/ingredients, and a read action.

## 1. Prepare the spreadsheet

1. Create a new Google Sheet (or reuse the existing shared one).
2. Add three tabs named exactly `MenuPlan`, `MenuIngredients`, and `ShoppingList`.
3. Import the CSV templates from `sheets-template/` into their matching tabs (File → Import → Upload → Replace data). This seeds the headers that the script expects.

## 2. Open the Apps Script editor

1. With the sheet open, choose **Extensions → Apps Script**.
2. Remove any placeholder code (`function myFunction(){}`).
3. Copy the full contents of `apps-script/Code.gs` from this repository into the editor and save.

## 3. Configure the API token

1. In Apps Script, open **Project Settings** (gear icon).
2. Under *Script properties*, click **Add script property**.
3. Name: `MENU_API_TOKEN`.
4. Value: generate a long random string (32+ characters). This secret must match the one you embed inside the Android APK.
5. Save the property.

## 4. Deploy the web app

1. Click **Deploy → New deployment**.
2. Select **Web app**.
3. Execute as: **Me**.
4. Who has access: **Anyone with the link** (the token still protects the API).
5. Confirm the authorization prompts and copy the Web App URL ending in `/exec`.

## 5. Share the sheet

Invite each collaborator (Editor access) so everyone can also tweak the spreadsheet manually if needed. Because the web app executes as you, collaborators only need the token and URL to sync through the Android app.

## 6. Test the endpoint

Open a browser tab to `https://script.google.com/macros/s/<deployment-id>/exec?action=listState&token=<MENU_API_TOKEN>`. You should receive JSON with three arrays: `proposals`, `ingredients`, and `shopping`.

- `Unauthorized` → token mismatch.
- `API token is not configured` → double-check the script property name.
- Any other error will also appear in **Executions** inside Apps Script.

## 7. Redeploying updates

Whenever you edit `Code.gs`, use **Deploy → Manage deployments → Edit** to publish a new version. The URL stays the same.

## 8. Link the Android app

Before building the APK, open `android-app/app/src/main/java/com/menumanager/AppSecrets.kt` and paste:

- `BASE_URL` → the `/exec` URL from step 4.
- `API_TOKEN` → the value from step 3.

Rebuild (`./gradlew.bat assembleDebug`) and distribute the APK. No runtime configuration screen is needed—the app ships ready for your household.

### Current deployment snapshot

These are the values presently embedded in the published APK and repository:

- `BASE_URL`: `https://script.google.com/macros/s/AKfycbyPjNnr8vHhEadraXjc5Gt6s7I3rp_zCux8L9vO_gilCpjbTEh3bjdF5v5jC4Cc2W4Log/exec`
- `API_TOKEN`: `deae76a08a291d29d328364151126dedfae61e358882210e968e5655a10b785c`
