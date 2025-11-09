# Firestore security rules (household-based)

Apply these rules in Firebase console > Firestore Database > Rules after enabling Anonymous Auth. They require each caller to be authenticated and to belong to the household whose documents they are touching.

## Rules (copy/paste)

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    function isMemberOf(householdId) {
      return request.auth != null &&
             request.auth.uid in get(/databases/$(database)/documents/households/$(householdId)).data.members;
    }

    match /households/{householdId} {
      allow read, write: if isMemberOf(householdId);

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

    match /households/{householdId} {
      allow create: if request.auth != null &&
        request.auth.uid in request.resource.data.members;
    }
  }
}
```

## Security notes

- Anonymous auth is still authentication. Ensure the Android app signs in before hitting Firestore; otherwise every request is rejected.
- `isMemberOf` fetches the household document to confirm that the caller UID appears inside `members`. Firestore caches reads, so the extra lookup is inexpensive.
- Joining a household happens client-side: the app queries by invite code and uses `FieldValue.arrayUnion(uid)`. For extra protection you can replicate this logic in a callable Cloud Function.

## Temporary test mode (development only)

Use the snippet below only during the very first setup. Switch back to the secure rules above before releasing or sharing builds.

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if true; // open to the world, do not use in production
    }
  }
}
```
