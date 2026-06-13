"use strict";

const {initializeApp} = require("firebase-admin/app");
const {getFirestore, FieldValue, Timestamp} = require("firebase-admin/firestore");
const {getMessaging} = require("firebase-admin/messaging");
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {onDocumentCreated} = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");

initializeApp();

const db = getFirestore();
const REGION = "europe-west1";
const CATEGORIES = new Set(["chat", "ranking", "rewards", "other"]);
const USER_ACTIONS = new Set([
  "chat_thread",
  "friend_accept",
  "game_invite",
  "challenge_result",
  "tournament_result",
]);
const SYSTEM_ACTIONS = new Set([
  "rank_detail",
  "rewards_claim",
  "league_info",
]);
const ACTION_CATEGORIES = {
  chat_thread: "chat",
  rank_detail: "ranking",
  rewards_claim: "rewards",
  friend_accept: "other",
  game_invite: "other",
  league_info: "ranking",
  challenge_result: "other",
  tournament_result: "other",
};

exports.sendUserNotification = onCall({region: REGION}, async (request) => {
  const senderId = requireAuth(request);
  const payload = validatePayload(request.data, USER_ACTIONS);
  if (payload.recipientId === senderId) {
    throw new HttpsError("invalid-argument", "Notifikacija se ne salje samom sebi.");
  }
  const notification = await createNotification(payload.recipientId, {
    ...payload,
    senderId,
    source: "user",
  });
  return {notificationId: notification.id};
});

exports.createSystemNotification = onCall({region: REGION}, async (request) => {
  requireAuth(request);
  if (request.auth.token.admin !== true) {
    throw new HttpsError("permission-denied", "Potrebna je administratorska uloga.");
  }
  const payload = validatePayload(request.data, SYSTEM_ACTIONS);
  const notification = await createNotification(payload.recipientId, {
    ...payload,
    senderId: null,
    source: "system",
  });
  return {notificationId: notification.id};
});

exports.createDemoNotifications = onCall({region: REGION}, async (request) => {
  const userId = requireAuth(request);
  const demoItems = [
    {
      category: "chat",
      action: "chat_thread",
      title: "Nova poruka u cetu",
      message: "marko99: Kad igramo sledecu partiju?",
      targetId: "demo-regional-chat",
    },
    {
      category: "ranking",
      action: "rank_detail",
      title: "Plasman na rang listi",
      message: "Trenutno si 142. na nedeljnoj rang listi.",
      targetId: "demo-weekly-ranking",
    },
    {
      category: "rewards",
      action: "rewards_claim",
      title: "Nagrada je dostupna",
      message: "Osvojila si 3 tokena za plasman u prethodnom ciklusu.",
      targetId: "demo-reward",
    },
    {
      category: "other",
      action: "friend_accept",
      title: "Zahtev za prijateljstvo",
      message: "ana_me ti je poslala zahtev za prijateljstvo.",
      targetId: "demo-friend-request",
    },
    {
      category: "ranking",
      action: "league_info",
      title: "Nova liga",
      message: "Presla si iz Srebrne u Zlatnu ligu.",
      targetId: "demo-league",
    },
  ];
  const references = await Promise.all(demoItems.map((item) =>
    createNotification(userId, {
      ...item,
      data: {demo: "true"},
      senderId: null,
      source: "demo",
      expiresInSeconds: null,
    })));
  return {notificationIds: references.map((reference) => reference.id)};
});

exports.pushNotification = onDocumentCreated({
  region: REGION,
  document: "users/{userId}/notifications/{notificationId}",
}, async (event) => {
  if (!event.data) {
    return;
  }
  const notification = event.data.data();
  const devices = await db.collection("users").doc(event.params.userId)
      .collection("devices").get();
  const tokenDocs = devices.docs.filter((document) => {
    const token = document.get("token");
    return typeof token === "string" && token.length > 0;
  });
  if (tokenDocs.length === 0) {
    await event.data.ref.update({
      pushStatus: "no_devices",
      pushProcessedAt: FieldValue.serverTimestamp(),
    });
    return;
  }

  const data = stringData({
    notificationId: event.params.notificationId,
    category: notification.category,
    action: notification.action,
    targetId: notification.targetId,
    title: notification.title,
    message: notification.message,
  });
  const response = await getMessaging().sendEachForMulticast({
    tokens: tokenDocs.map((document) => document.get("token")),
    data,
    android: {
      priority: "high",
    },
  });

  const invalidDeletes = [];
  response.responses.forEach((result, index) => {
    if (!result.success && isInvalidToken(result.error)) {
      invalidDeletes.push(tokenDocs[index].ref.delete());
    }
  });
  await Promise.all(invalidDeletes);
  await event.data.ref.update({
    pushStatus: response.failureCount === 0 ? "sent" : "partial",
    pushSuccessCount: response.successCount,
    pushFailureCount: response.failureCount,
    pushProcessedAt: FieldValue.serverTimestamp(),
  });
});

async function createNotification(recipientId, payload) {
  const user = await db.collection("users").doc(recipientId).get();
  if (!user.exists) {
    throw new HttpsError("not-found", "Primalac ne postoji.");
  }
  const reference = db.collection("users").doc(recipientId)
      .collection("notifications").doc();
  await reference.set({
    category: payload.category,
    title: payload.title,
    message: payload.message,
    action: payload.action,
    targetId: payload.targetId,
    data: payload.data,
    senderId: payload.senderId,
    source: payload.source,
    read: false,
    readAt: null,
    actionedAt: null,
    createdAt: FieldValue.serverTimestamp(),
    expiresAt: payload.expiresInSeconds === null
      ? null
      : Timestamp.fromMillis(Date.now() + payload.expiresInSeconds * 1000),
  });
  return reference;
}

function validatePayload(raw, allowedActions) {
  const data = raw && typeof raw === "object" ? raw : {};
  const category = requiredString(data.category, "category", 20).toLowerCase();
  const action = requiredString(data.action, "action", 40);
  if (!CATEGORIES.has(category)) {
    throw new HttpsError("invalid-argument", "Nepoznata kategorija.");
  }
  if (!allowedActions.has(action)) {
    throw new HttpsError("permission-denied", "Ova akcija nije dozvoljena.");
  }
  if (ACTION_CATEGORIES[action] !== category) {
    throw new HttpsError("invalid-argument", "Kategorija ne odgovara akciji.");
  }
  return {
    recipientId: requiredString(data.recipientId, "recipientId", 128),
    category,
    action,
    title: requiredString(data.title, "title", 100),
    message: requiredString(data.message, "message", 500),
    targetId: optionalString(data.targetId, 256),
    data: sanitizeData(data.data),
    expiresInSeconds: parseExpiry(data.expiresInSeconds),
  };
}

function requireAuth(request) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Korisnik nije prijavljen.");
  }
  return request.auth.uid;
}

function requiredString(value, field, maxLength) {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new HttpsError("invalid-argument", `Polje ${field} je obavezno.`);
  }
  return value.trim().slice(0, maxLength);
}

function optionalString(value, maxLength) {
  return typeof value === "string" ? value.trim().slice(0, maxLength) : "";
}

function sanitizeData(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return {};
  }
  const result = {};
  Object.entries(value).slice(0, 20).forEach(([key, item]) => {
    if (typeof item === "string") {
      result[String(key).slice(0, 50)] = item.slice(0, 500);
    }
  });
  return result;
}

function parseExpiry(value) {
  if (value === undefined || value === null) {
    return null;
  }
  const seconds = Number(value);
  if (!Number.isInteger(seconds) || seconds < 1 || seconds > 604800) {
    throw new HttpsError("invalid-argument", "Neispravno trajanje notifikacije.");
  }
  return seconds;
}

function stringData(value) {
  return Object.fromEntries(Object.entries(value)
      .map(([key, item]) => [key, item == null ? "" : String(item)]));
}

function isInvalidToken(error) {
  const code = error && error.code ? error.code : "";
  return code === "messaging/invalid-registration-token" ||
    code === "messaging/registration-token-not-registered";
}

process.on("unhandledRejection", (error) => {
  logger.error("Unhandled notification backend error", error);
});
