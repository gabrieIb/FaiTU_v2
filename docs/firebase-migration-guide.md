# Migrazione Firebase - Guida completa

Questa guida documenta il percorso di migrazione da Google Sheets + Apps Script a Firebase (Firestore + Auth anonima + funzioni opzionali). Il lavoro è completo: questa pagina resta come diario tecnico e checklist se dovessimo ripetere il processo in un nuovo progetto.

## Stato finale

✅ **Completato**
- Dipendenze Firebase configurate in `android-app/app/build.gradle.kts` (solo Auth + Firestore, niente più OkHttp o WorkManager legacy).
- `google-services.json` incluso nella build locale (da aggiungere manualmente per ogni ambiente).
- Sign-in anonimo automatico all'avvio (`MenuManagerApp.kt`).
- Persistenza offline Firestore attiva.
- Schema household in produzione: `households/{householdId}/proposals|ingredients|shopping`.
- `HouseholdRepository`, `HouseholdViewModel`, `HouseholdSetupScreen` collegati a `MainActivity`.
- `FirestoreMenuDataSource` e `MenuRepository` gestiscono letture/scritture realtime.
- Regole di sicurezza aggiornate (`docs/firestore-security-rules.md`).
- Documentazione aggiornata (`README.md`, `docs/firebase-backend-setup.md`, `docs/data-schema.md`).
- Vecchia infrastruttura Google Sheets/AppSecrets dismessa.

ℹ️ **Facoltativo**
- Le Cloud Functions in `functions/` restano disponibili se si desidera rigenerare la shopping list lato server o gestire il join con callable. L'app funziona anche senza deployarle.
- Per migrare ulteriori dati storici è possibile utilizzare gli script alla sezione 6.

---

## 1. Firebase Console Setup

### A) Scarica google-services.json

1. Apri https://console.firebase.google.com/u/2/project/faitu-6355b
2. Project settings → Your apps → Android
3. Se non hai ancora aggiunto l'app:
   - Clicca "Add app" → Android
   - Package name: `com.menumanager`
   - App nickname: "Fai tu!" (opzionale)
4. Scarica `google-services.json`
5. **Copialo in**: `android-app/app/google-services.json`

### B) Abilita Authentication Anonymous

1. Firebase console → Build → Authentication
2. Get started → Sign-in method
3. Abilita "Anonymous" → Save

### C) Crea Firestore Database

1. Firebase console → Build → Firestore Database
2. Create database
3. Scegli:
   - **Edition**: Standard edition (non Enterprise/MongoDB)
   - **Location**: europe-west1 (Belgium) o altra EU vicina
   - **Rules**: Start in test mode (temporaneo)

### D) Applica regole sicurezza

1. Firestore → Rules tab
2. Copia e incolla il contenuto di `docs/firestore-security-rules.md`
3. Publish

---

## 2. Build Android App

Dopo aver aggiunto `google-services.json`:

```powershell
cd "android-app"
.\gradlew.bat clean assembleDebug
```

Se compila, installa l'APK su dispositivo/emulatore:

```powershell
.\gradlew.bat installDebug
```

---

## 3. Collegare Household Setup a MainActivity

Modifica `MainActivity.kt` per mostrare `HouseholdSetupScreen` se l'utente non ha ancora configurato la famiglia.

**Bozza modifiche (da implementare):**

```kotlin
@Composable
fun MenuApp(viewModel: MenuViewModel) {
    val app = LocalContext.current.applicationContext as MenuManagerApp
    val householdViewModel: HouseholdViewModel by viewModels {
        HouseholdViewModelFactory(app.container.householdRepository)
    }
    val householdState by householdViewModel.state.collectAsStateWithLifecycle()

    when (householdState) {
        is HouseholdState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is HouseholdState.NeedsSetup,
        is HouseholdState.Creating,
        is HouseholdState.Joining,
        is HouseholdState.Error -> {
            HouseholdSetupScreen(
                state = householdState,
                onCreateHousehold = { householdViewModel.createHousehold() },
                onJoinHousehold = { code -> householdViewModel.joinHousehold(code) },
                onDismissError = { householdViewModel.dismissError() }
            )
        }
        is HouseholdState.Ready -> {
            // Mostra l'app principale (menu, spesa, ecc.)
            MenuAppContent(viewModel = viewModel)
        }
    }
}
```

E separa la logica attuale in `MenuAppContent()`.

---

## 4. Deploy Cloud Functions (opzionale ma consigliato)

Le Cloud Functions rigenerano automaticamente la shopping list quando cambiano ingredienti/proposte.

### Setup

```bash
cd functions
npm install
```

### Build

```bash
npm run build
```

### Deploy

```bash
firebase use faitu-6355b
firebase deploy --only functions
```

Funzioni deployate:
- `regenerateShoppingListOnIngredient`: trigger su write ingredienti
- `regenerateShoppingListOnProposal`: trigger su delete proposte
- `joinHousehold`: (opzionale) callable per join sicuro server-side

---

## 5. Testare il flusso

### Primo dispositivo (crea famiglia)

1. Avvia app → schermata setup
2. Tap "Crea una nuova famiglia"
3. L'app crea household e mostra codice invito a 6 caratteri (es. `A3F9K2`)
4. Copia il codice

### Secondo dispositivo (unisciti)

1. Avvia app → schermata setup
2. Tap "Unisciti con codice invito"
3. Inserisci il codice
4. Tap "Unisciti"

### Verifica sincronizzazione

- Crea una proposta su device A → dovrebbe apparire in realtime su device B
- Aggiungi ingrediente → shopping list si aggiorna su entrambi

---

## 6. Migrazione dati esistenti (opzionale)

Se hai già dati in Sheets, puoi importarli in Firestore:

### Script Node.js (bozza)

```javascript
const admin = require('firebase-admin');
const serviceAccount = require('./serviceAccountKey.json');

admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
const db = admin.firestore();

async function importData() {
  const householdId = 'YOUR_HOUSEHOLD_ID'; // recuperato dopo creazione famiglia
  
  // Esempio: importa proposals da CSV/JSON
  const proposals = [
    { proposalId: 'uuid1', mealSlot: 'Pranzo', title: 'Pasta', notes: '', createdBy: 'app', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }
  ];
  
  const batch = db.batch();
  proposals.forEach(p => {
    const ref = db.collection('households').doc(householdId).collection('proposals').doc(p.proposalId);
    batch.set(ref, p);
  });
  
  await batch.commit();
  console.log('Import completato');
}

importData();
```

Esegui:
```bash
node import.js
```

---

## 7. Disabilitare Sheets (dopo test completi)

Una volta verificato che Firebase funziona:

1. Rimuovi `MenuApiClient` e logica pending operations da `MenuRepository`
2. Imposta flag `useFirebase = true` in `ServiceLocator`
3. Archivia `apps-script/Code.gs`
4. Aggiorna `README.md` e `docs/` con nuova architettura

---

## Prossimi step immediati

1. ✅ Aggiungi `google-services.json` → poi ricompila
2. ✅ Abilita Auth Anonymous in console
3. ✅ Crea Firestore database con regole
4. 🔲 Integra `HouseholdSetupScreen` in `MainActivity`
5. 🔲 Testa su 2 dispositivi
6. 🔲 Deploy Cloud Functions
7. 🔲 Migra dati esistenti (se necessario)

---

## Troubleshooting

### Build fallisce con "google-services.json missing"
→ Assicurati che il file sia in `android-app/app/google-services.json` (non in sottocartelle)

### "User not authenticated" in Firestore
→ Verifica che Auth Anonymous sia abilitata e che l'app faccia sign-in all'avvio

### Regole Firestore rifiutano scrittura
→ Controlla che le regole siano pubblicate e che `request.auth.uid` sia in `members`

### Shopping list non si rigenera automaticamente
→ Deploy Cloud Functions e verifica log: `firebase functions:log`

---

## Riferimenti

- [Firestore Security Rules](https://firebase.google.com/docs/firestore/security/get-started)
- [Cloud Functions](https://firebase.google.com/docs/functions)
- [Auth Anonymous](https://firebase.google.com/docs/auth/android/anonymous-auth)
