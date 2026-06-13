package com.example.slagalicatim04.notifications;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationRepository {

    public interface Listener {
        void onChanged(List<InAppNotification> notifications);

        void onError(Exception error);
    }

    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;

    public NotificationRepository() {
        this(FirebaseAuth.getInstance(), FirebaseFirestore.getInstance());
    }

    NotificationRepository(FirebaseAuth auth, FirebaseFirestore firestore) {
        this.auth = auth;
        this.firestore = firestore;
    }

    public ListenerRegistration listen(@NonNull Listener listener) {
        CollectionReference notifications = currentUserNotifications();
        if (notifications == null) {
            listener.onError(new IllegalStateException("Korisnik nije prijavljen."));
            return () -> {
            };
        }
        return notifications.orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(200)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        listener.onError(error);
                        return;
                    }
                    List<InAppNotification> result = new ArrayList<>();
                    if (snapshot != null) {
                        snapshot.getDocuments().forEach(document ->
                                result.add(InAppNotification.fromDocument(document)));
                    }
                    listener.onChanged(result);
                });
    }

    public void markRead(String notificationId, Runnable onSuccess,
                         java.util.function.Consumer<Exception> onError) {
        CollectionReference notifications = currentUserNotifications();
        if (notifications == null) {
            onError.accept(new IllegalStateException("Korisnik nije prijavljen."));
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("read", true);
        updates.put("readAt", FieldValue.serverTimestamp());
        notifications.document(notificationId).update(updates)
                .addOnSuccessListener(ignored -> onSuccess.run())
                .addOnFailureListener(onError::accept);
    }

    public void recordAction(String notificationId, Runnable onSuccess,
                             java.util.function.Consumer<Exception> onError) {
        CollectionReference notifications = currentUserNotifications();
        if (notifications == null) {
            onError.accept(new IllegalStateException("Korisnik nije prijavljen."));
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("read", true);
        updates.put("readAt", FieldValue.serverTimestamp());
        updates.put("actionedAt", FieldValue.serverTimestamp());
        notifications.document(notificationId).update(updates)
                .addOnSuccessListener(ignored -> onSuccess.run())
                .addOnFailureListener(onError::accept);
    }

    public void createDemoNotifications(
            java.util.function.Consumer<List<InAppNotification>> onSuccess,
            java.util.function.Consumer<Exception> onError) {
        CollectionReference notifications = currentUserNotifications();
        if (notifications == null) {
            onError.accept(new IllegalStateException("Korisnik nije prijavljen."));
            return;
        }

        List<InAppNotification> demoItems = DemoNotifications.create();
        WriteBatch batch = firestore.batch();
        for (InAppNotification item : demoItems) {
            Map<String, Object> data = new HashMap<>();
            data.put("category", item.categoryKey());
            data.put("title", item.title);
            data.put("message", item.message);
            data.put("action", item.actionHint);
            data.put("targetId", item.targetId);
            data.put("data", item.data);
            data.put("source", "demo");
            data.put("read", false);
            data.put("readAt", null);
            data.put("actionedAt", null);
            data.put("createdAt", FieldValue.serverTimestamp());
            batch.set(notifications.document(item.id), data);
        }
        batch.commit()
                .addOnSuccessListener(ignored -> onSuccess.accept(demoItems))
                .addOnFailureListener(onError::accept);
    }

    private CollectionReference currentUserNotifications() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            return null;
        }
        return firestore.collection("users")
                .document(user.getUid())
                .collection("notifications");
    }
}
