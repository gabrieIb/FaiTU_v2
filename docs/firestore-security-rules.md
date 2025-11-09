# Regole di sicurezza Firestore (household-based)

Questo file contiene le regole da applicare in Firebase console → Firestore Database → Rules.

## Regole (copia/incolla in console)

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // Helper: verifica che l'utente autenticato sia membro della famiglia
    function isMemberOf(householdId) {
      return request.auth != null &&
             request.auth.uid in get(/databases/$(database)/documents/households/$(householdId)).data.members;
    }

    // Household root: lettura/scrittura solo per membri
    match /households/{householdId} {
      allow read: if isMemberOf(householdId);
      allow write: if isMemberOf(householdId);
      
      // Sottocollezioni: proposals, ingredients, shopping
      match /proposals/{proposalId} {
        allow read, write: if isMemberOf(householdId);
      }
      
      match /ingredients/{ingredientId} {
        allow read, write: if isMemberOf(householdId);
      }
      
      match /shopping/{shoppingId} {
        allow read, write: if isMemberOf(householdId);
      }
    }
    
    // Permetti a qualunque utente autenticato di creare una nuova famiglia
    match /households/{householdId} {
      allow create: if request.auth != null && 
                       request.auth.uid in request.resource.data.members;
    }
  }
}
```

## Note di sicurezza

- **Auth anonima obbligatoria**: le regole verificano `request.auth != null`, quindi ogni chiamata deve provenire da un utente autenticato (anche anonimo).
- **Membership check**: `isMemberOf()` legge il documento household e verifica che l'uid corrente sia nella lista `members`.
- **Join via app**: l'operazione di "join" (aggiungere un uid a `members`) è fatta tramite l'app client. Per massima sicurezza, si può spostare questa logica in una Cloud Function callable che valida il codice invito server-side.

## Test mode (solo sviluppo iniziale)

Se vuoi iniziare senza autenticazione per testare velocemente, usa temporaneamente:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if true; // ATTENZIONE: aperto a tutti, solo per test!
    }
  }
}
```

Poi passa alle regole sopra non appena abiliti l'auth anonima.
