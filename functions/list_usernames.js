"use strict";

const {initializeApp, cert} = require("firebase-admin/app");
const {getFirestore} = require("firebase-admin/firestore");
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
  initializeApp({credential: cert(loadServiceAccount())});
  const db = getFirestore();
  const snapshot = await db.collection("users").get();
  const users = snapshot.docs.map((doc) => ({
    id: doc.id,
    username: String(doc.get("username") || ""),
    inGame: doc.get("inGame") === true,
    busy: doc.get("busy") === true,
    isPlaying: doc.get("isPlaying") === true,
    currentRoomId: String(doc.get("currentRoomId") || ""),
    currentMatchId: String(doc.get("currentMatchId") || ""),
  }));
  console.log(JSON.stringify(users, null, 2));
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
