# Notifications backend

Notifications are stored at:

`users/{userId}/notifications/{notificationId}`

Registered devices are stored at:

`users/{userId}/devices/{sha256OfFcmToken}`

## Notification document

- `category`: `chat`, `ranking`, `rewards`, or `other`
- `title`, `message`
- `action`: navigation action from `NotificationRouter`
- `targetId`: ID of a chat, invitation, cycle, reward, or other future entity
- `data`: additional string metadata
- `read`, `readAt`, `actionedAt`
- `createdAt`, optional `expiresAt`
- `senderId`, `source`

## Sending

Future chat, friends, and match modules call `NotificationDispatchService.send`.
The callable function validates the event, writes the history entry, and the
Firestore trigger sends FCM to every registered Android device.

Ranking, reward, and league jobs call `createSystemNotification` from a trusted
administrator account. These actions cannot be forged by a regular client.

## Deployment

1. Merge `firestore.notifications.rules` into the project's existing
   `firestore.rules`.
2. In `functions`, run `npm install`.
3. Deploy with `firebase deploy --only functions`.

The Android application works with Firestore history immediately after the
rules and functions are deployed. Destinations that are not implemented yet
open `NotificationTargetFragment` and preserve `action` and `targetId`, so they
can later be replaced with real navigation without changing stored data.
