# Cloud Functions per Menu App

Questo progetto contiene le Cloud Functions necessarie per l'app menu.

## Setup

1. Installa Firebase CLI:
```bash
npm install -g firebase-tools
```

2. Login Firebase:
```bash
firebase login
```

3. Inizializza progetto (se non già fatto):
```bash
firebase init functions
```
- Scegli TypeScript
- Installa dipendenze

4. Deploy:
```bash
firebase deploy --only functions
```

## Funzioni disponibili

### regenerateShoppingList (trigger onWrite)

Rigenera automaticamente la lista della spesa quando cambiano ingredienti o proposte.

- **Trigger**: `households/{householdId}/ingredients/{ingredientId}` e `proposals/{proposalId}`
- **Logica**:
  - Filtra ingredienti con `needToBuy: true`
  - Preserva voci manuali (quelle con `ingredientId` null)
  - Crea/aggiorna documenti in `shopping`

### joinHousehold (callable, opzionale per maggiore sicurezza)

Permette di unirsi a una famiglia validando il codice invito server-side.

- **Input**: `{ inviteCode: string }`
- **Output**: `{ householdId: string }`
- **Errore**: se codice non valido

Nota: l'implementazione attuale gestisce il join lato client. Questa function è opzionale per massimizzare sicurezza.

## Prossimi passi

- Implementare `regenerateShoppingList` per automatizzare il rebuild della lista spesa
- (Opzionale) Implementare `joinHousehold` callable per validazione server-side del codice invito
