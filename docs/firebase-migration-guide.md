# Firebase migration guide

This document records the migration path from Google Sheets + Apps Script to Firebase (Firestore + Anonymous Auth + optional Cloud Functions). The work is complete; keep this page as a reference if we ever repeat the process in another project.

## Final state snapshot

- Firebase Auth (anonymous) and Firestore are the only runtime dependencies.
- `google-services.json` is provided per environment under `android-app/app/`.
- Realtime access flows through HouseholdRepository (invite codes + membership) and FirestoreMenuDataSource (proposals, ingredients, shopping).
- Offline persistence is enabled; menu data survives short network drops.
- Firestore security rules restrict reads and writes to household members only.
- Documentation updated: README.md, docs/firebase-backend-setup.md, docs/data-schema.md, docs/firestore-security-rules.md.
- Legacy Google Sheets code (MenuApiClient, Apps Script) is archived but unused.

## Migration checklist

1. **Prepare Firebase project**
   - Create or reuse a Firebase project.
   - Enable Anonymous Auth.
   - Create Firestore (production mode) and apply the rules from docs/firestore-security-rules.md.
2. **Wire the Android app**
   - Add Firebase BoM, Auth, and Firestore dependencies to `android-app/app/build.gradle.kts`.
   - Apply the Google Services Gradle plugin.
   - Drop legacy OkHttp/WorkManager dependencies, along with pending operations and token storage.
3. **Add household onboarding**
   - Implement HouseholdRepository (DataStore + Firestore) and expose it via ServiceLocator.
   - Create HouseholdViewModel, HouseholdViewModelFactory, and HouseholdSetupScreen.
   - Gate MenuApp so it waits for HouseholdState.Ready before streaming menu data.
4. **Implement Firestore data layer**
   - MenuRemoteDataSource now backed solely by FirestoreMenuDataSource (snapshot listeners + suspend writes).
   - Map Firestore documents to domain models (RemoteProposal, RemoteIngredient, RemoteShoppingItem).
   - Update MenuRepository to drop HTTP sync/pending operations and rely on Firestore flows.
5. **Update UI + cache**
   - MenuViewModel listens to Firestore streams and merges manual shopping entries with derived ones from ingredients marked `needToBuy = true`.
   - MenuCache keeps the latest snapshot used by Compose screens.
6. **Optional Cloud Functions**
   - Functions under `functions/` mirror ingredient changes into the shopping collection and expose a callable join helper.
   - Deployment commands documented in docs/firebase-backend-setup.md.
7. **Testing**
   - Unit tests updated to mock the Firestore data source via fake flows.
   - Manual QA: create a household, share invite code, verify two devices stay in sync, delete proposals to confirm cascading deletes.
8. **Data migration (if legacy data exists)**
   - Export CSV from Sheets, transform to JSON, and import with a small Node script using the Admin SDK (see example below).

## Sample import script

```javascript
const admin = require('firebase-admin');
const serviceAccount = require('./serviceAccountKey.json');

admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
const db = admin.firestore();

async function importHousehold(householdId, data) {
  const batch = db.batch();
  data.proposals.forEach(item => {
    const ref = db.collection('households').doc(householdId).collection('proposals').doc(item.proposalId);
    batch.set(ref, item);
  });
  data.ingredients.forEach(item => {
    const ref = db.collection('households').doc(householdId).collection('ingredients').doc(item.ingredientId);
    batch.set(ref, item);
  });
  data.shopping.forEach(item => {
    const ref = db.collection('households').doc(householdId).collection('shopping').doc(item.shoppingId);
    batch.set(ref, item);
  });
  await batch.commit();
}

importHousehold('YOUR_HOUSEHOLD_ID', require('./export.json')).then(() => {
  console.log('Import completed');
});
```

## Troubleshooting notes

- **google-services.json missing:** place the file under `android-app/app/` and re-run the build.
- **User not authenticated:** ensure anonymous auth is enabled and check logcat for FirebaseAuth initialization errors.
- **Rules denying writes:** confirm the invite flow added the current UID to the members array.
- **Shopping list duplicates:** deploy the optional Cloud Function or dedupe locally by ingredientId (already handled in MenuViewModel).

Repeat these steps if you spin up a fresh environment; otherwise keep the Firebase project running and focus on app features.

