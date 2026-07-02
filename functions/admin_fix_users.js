"use strict";

const {initializeApp, cert} = require("firebase-admin/app");
const {getFirestore, FieldValue} = require("firebase-admin/firestore");
const fs = require("fs");
const path = require("path");

function loadServiceAccount() {
  const explicitPath = process.argv[2] || process.env.GOOGLE_APPLICATION_CREDENTIALS;
  if (!explicitPath) {
    throw new Error("Missing service account path.");
  }
  const resolved = path.resolve(explicitPath);
  return JSON.parse(fs.readFileSync(resolved, "utf8"));
}

async function main() {
  const serviceAccount = loadServiceAccount();
  initializeApp({credential: cert(serviceAccount)});
  const db = getFirestore();

  const usersSnapshot = await db.collection("users").get();
  const batchSize = 400;
  let batch = db.batch();
  let opCount = 0;

  const clearBusyState = {
    inGame: false,
    busy: false,
    isPlaying: false,
    currentRoomId: FieldValue.delete(),
    currentMatchId: FieldValue.delete(),
    currentOpponentId: FieldValue.delete(),
    activeRoomId: FieldValue.delete(),
    activeMatchId: FieldValue.delete(),
    lastActiveAt: Date.now(),
  };

  const targetUsernames = new Set(["milso1", "milos1", "marceta"]);
  const clearedUsers = [];

  for (const doc of usersSnapshot.docs) {
    const username = String(doc.get("username") || "").trim().toLowerCase();
    const updates = {
      tokens: 200,
      stars: 200,
      totalStars: 200,
      overallStars: 200,
    };

    if (targetUsernames.has(username)) {
      Object.assign(updates, clearBusyState);
      clearedUsers.push({id: doc.id, username});
    }

    batch.set(doc.ref, updates, {merge: true});
    opCount++;

    if (opCount >= batchSize) {
      await batch.commit();
      batch = db.batch();
      opCount = 0;
    }
  }

  if (opCount > 0) {
    await batch.commit();
  }

  console.log(JSON.stringify({
    updatedUsers: usersSnapshot.size,
    clearedUsers,
  }, null, 2));
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
