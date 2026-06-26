package com.example.slagalicatim04.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.slagalicatim04.R;
import com.example.slagalicatim04.databinding.FragmentNotificationsBinding;
import com.example.slagalicatim04.friends.GameInviteRepository;
import com.example.slagalicatim04.notifications.InAppNotification;
import com.example.slagalicatim04.notifications.NotificationRepository;
import com.example.slagalicatim04.notifications.NotificationRouter;
import com.example.slagalicatim04.notifications.NotificationService;
import com.example.slagalicatim04.notifications.NotificationsAdapter;
import com.example.slagalicatim04.notifications.SystemNotificationPublisher;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class NotificationsFragment extends Fragment implements NotificationsAdapter.Listener {

    private enum FilterMode {
        ALL, UNREAD, READ
    }

    private FragmentNotificationsBinding binding;
    private NotificationsAdapter adapter;
    private final List<InAppNotification> masterList = new ArrayList<>();
    private final NotificationService notificationService = new NotificationService();
    private ListenerRegistration listenerRegistration;
    private FilterMode filterMode = FilterMode.ALL;
    private boolean initialDemoRequested;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentNotificationsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        adapter = new NotificationsAdapter(this);
        binding.notificationsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.notificationsList.setAdapter(adapter);

        attachFilterListeners();
        binding.notificationsSendDemo.setOnClickListener(ignored ->
                requestDemoNotifications(false));
        refreshListUi();
        listenerRegistration = notificationService.observe(new NotificationRepository.Listener() {
            @Override
            public void onChanged(List<InAppNotification> notifications) {
                if (binding == null) {
                    return;
                }
                masterList.clear();
                masterList.addAll(notifications);
                scheduleInviteExpirations(notifications);
                refreshListUi();
                if (notifications.isEmpty() && !initialDemoRequested) {
                    requestDemoNotifications(true);
                }
            }

            @Override
            public void onError(Exception error) {
                if (binding != null) {
                    Snackbar.make(binding.getRoot(),
                            getString(R.string.notifications_load_error, messageOf(error)),
                            Snackbar.LENGTH_LONG).show();
                }
            }
        });
    }

    private void attachFilterListeners() {
        Chip chipAll = binding.chipFilterAll;
        Chip chipUnread = binding.chipFilterUnread;
        Chip chipRead = binding.chipFilterRead;

        chipAll.setOnCheckedChangeListener((button, checked) -> {
            if (checked) {
                filterMode = FilterMode.ALL;
                refreshListUi();
            }
        });
        chipUnread.setOnCheckedChangeListener((button, checked) -> {
            if (checked) {
                filterMode = FilterMode.UNREAD;
                refreshListUi();
            }
        });
        chipRead.setOnCheckedChangeListener((button, checked) -> {
            if (checked) {
                filterMode = FilterMode.READ;
                refreshListUi();
            }
        });
    }

    private List<InAppNotification> filtered() {
        return notificationService.filter(masterList,
                filterMode == FilterMode.READ,
                filterMode == FilterMode.UNREAD);
    }

    private void refreshListUi() {
        List<InAppNotification> shown = filtered();
        adapter.submitList(shown);
        boolean empty = shown.isEmpty();
        binding.notificationsEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.notificationsList.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onMarkRead(InAppNotification item) {
        notificationService.markRead(item,
                () -> {
                    if (binding != null) {
                        Snackbar.make(binding.getRoot(), R.string.notif_marked_read,
                                Snackbar.LENGTH_SHORT).show();
                    }
                },
                this::showOperationError);
    }

    @Override
    public void onOpen(InAppNotification item) {
        if (NotificationRouter.ACTION_GAME_INVITE.equals(item.actionHint)) {
            Snackbar.make(binding.getRoot(),
                    "Koristi dugmad Prihvati ili Odbij za poziv za partiju.",
                    Snackbar.LENGTH_SHORT).show();
            return;
        }
        notificationService.recordOpen(item, () -> {
        }, this::showOperationError);
        Navigation.findNavController(binding.getRoot())
                .navigate(R.id.notificationTargetFragment, NotificationRouter.targetArgs(item));
    }

    @Override
    public void onAcceptGameInvite(InAppNotification item) {
        String roomId = roomIdOf(item);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            showOperationError(new IllegalStateException("Korisnik nije prijavljen."));
            return;
        }
        new Thread(() -> {
            try {
                String acceptedRoomId = new GameInviteRepository()
                        .acceptInvite(user.getUid(), roomId, item.id);
                if (binding == null) {
                    return;
                }
                requireActivity().runOnUiThread(() -> {
                    Bundle args = new Bundle();
                    args.putString("roomId", acceptedRoomId);
                    Snackbar.make(binding.getRoot(), "Poziv je prihvacen.",
                            Snackbar.LENGTH_SHORT).show();
                    Navigation.findNavController(binding.getRoot())
                            .navigate(R.id.stepByStepWaitingRoomFragment, args);
                });
            } catch (Exception error) {
                showOperationError(error);
            }
        }).start();
    }

    @Override
    public void onDeclineGameInvite(InAppNotification item) {
        String roomId = roomIdOf(item);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            showOperationError(new IllegalStateException("Korisnik nije prijavljen."));
            return;
        }
        new Thread(() -> {
            try {
                new GameInviteRepository().declineInvite(user.getUid(), roomId, item.id);
                if (binding != null) {
                    requireActivity().runOnUiThread(() ->
                            Snackbar.make(binding.getRoot(), "Poziv je odbijen.",
                                    Snackbar.LENGTH_SHORT).show());
                }
            } catch (Exception error) {
                showOperationError(error);
            }
        }).start();
    }

    private void scheduleInviteExpirations(List<InAppNotification> notifications) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (InAppNotification item : notifications) {
            if (!NotificationRouter.ACTION_GAME_INVITE.equals(item.actionHint)
                    || !"pending".equals(item.inviteStatus)) {
                continue;
            }
            long expiresAt = longValue(item.data.get("expiresAt"));
            if (expiresAt <= 0L) {
                continue;
            }
            long delay = Math.max(0L, expiresAt - now);
            handler.postDelayed(() -> new Thread(() -> {
                try {
                    new GameInviteRepository().expireInviteIfNeeded(
                            user.getUid(), roomIdOf(item), item.id);
                } catch (Exception ignored) {
                }
            }).start(), delay);
        }
    }

    private void showOperationError(Exception error) {
        if (getActivity() != null && Looper.myLooper() != Looper.getMainLooper()) {
            requireActivity().runOnUiThread(() -> showOperationError(error));
            return;
        }
        if (binding != null) {
            Snackbar.make(binding.getRoot(),
                    getString(R.string.notifications_update_error, messageOf(error)),
                    Snackbar.LENGTH_LONG).show();
        }
    }

    private void requestDemoNotifications(boolean automatic) {
        if (automatic) {
            initialDemoRequested = true;
        }
        binding.notificationsSendDemo.setEnabled(false);
        notificationService.createDemoNotifications(
                items -> {
                    if (binding == null) {
                        return;
                    }
                    binding.notificationsSendDemo.setEnabled(true);
                    boolean shownInBar = false;
                    for (InAppNotification item : items) {
                        shownInBar |= SystemNotificationPublisher.show(requireContext(), item);
                    }
                    if (!automatic) {
                        Snackbar.make(binding.getRoot(),
                                shownInBar
                                        ? R.string.notifications_demo_sent
                                        : R.string.notifications_demo_saved_permission,
                                Snackbar.LENGTH_LONG).show();
                    }
                },
                error -> {
                    if (binding == null) {
                        return;
                    }
                    binding.notificationsSendDemo.setEnabled(true);
                    Snackbar.make(binding.getRoot(),
                            getString(R.string.notifications_demo_error, messageOf(error)),
                            Snackbar.LENGTH_LONG).show();
                });
    }

    private static String messageOf(Exception error) {
        return error.getMessage() == null ? "nepoznata greska" : error.getMessage();
    }

    private String roomIdOf(InAppNotification item) {
        String roomId = item.data.get("roomId");
        return roomId == null || roomId.trim().isEmpty() ? item.targetId : roomId;
    }

    private long longValue(String value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    @Override
    public void onDestroyView() {
        if (listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
        }
        super.onDestroyView();
        binding = null;
    }
}
