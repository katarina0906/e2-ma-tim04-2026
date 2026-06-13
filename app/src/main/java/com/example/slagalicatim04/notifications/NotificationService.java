package com.example.slagalicatim04.notifications;

import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;
import java.util.function.Consumer;

public class NotificationService {

    private final NotificationRepository repository;
    public NotificationService() {
        this(new NotificationRepository());
    }

    NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    public ListenerRegistration observe(NotificationRepository.Listener listener) {
        return repository.listen(listener);
    }

    public List<InAppNotification> filter(List<InAppNotification> source,
                                          boolean read, boolean unread) {
        if (read == unread) {
            return source;
        }
        java.util.ArrayList<InAppNotification> result = new java.util.ArrayList<>();
        for (InAppNotification item : source) {
            if ((read && item.read) || (unread && !item.read)) {
                result.add(item);
            }
        }
        return result;
    }

    public void markRead(InAppNotification item, Runnable onSuccess,
                         Consumer<Exception> onError) {
        if (item.read) {
            onSuccess.run();
            return;
        }
        repository.markRead(item.id, onSuccess, onError);
    }

    public void recordOpen(InAppNotification item, Runnable onSuccess,
                           Consumer<Exception> onError) {
        repository.recordAction(item.id, onSuccess, onError);
    }

    public void createDemoNotifications(Consumer<List<InAppNotification>> onSuccess,
                                        Consumer<Exception> onError) {
        repository.createDemoNotifications(onSuccess, onError);
    }
}
