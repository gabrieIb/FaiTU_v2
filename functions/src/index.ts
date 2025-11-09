import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

admin.initializeApp();
const db = admin.firestore();

/**
 * Rigenera la shopping list quando cambiano ingredienti o proposte.
 * Trigger: onWrite su households/{householdId}/ingredients/{ingredientId}
 */
export const regenerateShoppingListOnIngredient = functions.firestore
  .document("households/{householdId}/ingredients/{ingredientId}")
  .onWrite(async (change, context) => {
    const householdId = context.params.householdId;
    await regenerateShoppingList(householdId);
  });

/**
 * Rigenera anche quando si cancella una proposal (per rimuovere ingredienti orfani).
 */
export const regenerateShoppingListOnProposal = functions.firestore
  .document("households/{householdId}/proposals/{proposalId}")
  .onDelete(async (snap, context) => {
    const householdId = context.params.householdId;
    await regenerateShoppingList(householdId);
  });

/**
 * Logica di rigenerazione shopping list.
 */
async function regenerateShoppingList(householdId: string): Promise<void> {
  const ingredientsSnapshot = await db
    .collection("households")
    .doc(householdId)
    .collection("ingredients")
    .where("needToBuy", "==", true)
    .get();

  const shoppingRef = db
    .collection("households")
    .doc(householdId)
    .collection("shopping");

  // Ottieni voci manuali esistenti (quelle senza ingredientId)
  const existingSnapshot = await shoppingRef.get();
  const manualEntries: FirebaseFirestore.DocumentSnapshot[] = [];
  existingSnapshot.forEach((doc) => {
    const data = doc.data();
    if (!data?.ingredientId) {
      manualEntries.push(doc);
    }
  });

  // Cancella tutte le voci auto-generate (quelle con ingredientId)
  const batch = db.batch();
  existingSnapshot.forEach((doc) => {
    const data = doc.data();
    if (data?.ingredientId) {
      batch.delete(doc.ref);
    }
  });

  // Ricrea voci dalla lista ingredienti
  const now = admin.firestore.FieldValue.serverTimestamp();
  ingredientsSnapshot.forEach((ingredientDoc) => {
    const ingredient = ingredientDoc.data();
    const shoppingDoc = shoppingRef.doc(ingredientDoc.id); // usa ingredientId come shoppingId
    batch.set(shoppingDoc, {
      shoppingId: ingredientDoc.id,
      ingredientId: ingredientDoc.id,
      proposalId: ingredient.proposalId || "",
      name: ingredient.name || "",
      status: "pending",
      updatedAt: now,
    });
  });

  await batch.commit();
  console.log(`Shopping list regenerated for household ${householdId}`);
}

/**
 * (Opzionale) Callable function per unirsi a una famiglia.
 * Migliora sicurezza validando il codice server-side.
 */
export const joinHousehold = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Devi essere autenticato"
    );
  }

  const inviteCode: string = data.inviteCode?.trim().toUpperCase();
  if (!inviteCode || inviteCode.length !== 6) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Codice invito non valido"
    );
  }

  const householdsSnapshot = await db
    .collection("households")
    .where("inviteCode", "==", inviteCode)
    .limit(1)
    .get();

  if (householdsSnapshot.empty) {
    throw new functions.https.HttpsError(
      "not-found",
      "Codice invito non trovato"
    );
  }

  const householdDoc = householdsSnapshot.docs[0];
  const householdId = householdDoc.id;
  const uid = context.auth.uid;

  // Aggiungi uid ai members se non c'è già
  await db
    .collection("households")
    .doc(householdId)
    .update({
      members: admin.firestore.FieldValue.arrayUnion(uid),
    });

  return {householdId};
});
