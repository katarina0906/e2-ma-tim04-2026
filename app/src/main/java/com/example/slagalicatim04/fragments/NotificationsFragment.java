package com.example.slagalicatim04.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalicatim04.R;
import com.example.slagalicatim04.databinding.FragmentNotificationsBinding;
import com.example.slagalicatim04.notifications.InAppNotification;
import com.example.slagalicatim04.notifications.NotificationsAdapter;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

/** Ekran istorije sistemskih notifikacija (mock podaci, samo UI). */
public class NotificationsFragment extends Fragment implements NotificationsAdapter.Listener {

    private enum FilterMode {
        ALL, UNREAD, READ
    }

    private FragmentNotificationsBinding binding;
    private NotificationsAdapter adapter;
    private final List<InAppNotification> masterList = new ArrayList<>();
    private FilterMode filterMode = FilterMode.ALL;

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
        RecyclerView rv = binding.notificationsList;
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        if (masterList.isEmpty()) {
            masterList.addAll(buildSampleNotifications());
        }
        attachFilterListeners();
        refreshListUi();
    }

    private static List<InAppNotification> buildSampleNotifications() {
        List<InAppNotification> list = new ArrayList<>();
        list.add(new InAppNotification(
                "1",
                InAppNotification.Category.CHAT,
                "Nova poruka u četu",
                "marko99: Kad igramo?",
                "pre 10 min",
                false,
                "chat_thread"));
        list.add(new InAppNotification(
                "2",
                InAppNotification.Category.RANKING,
                "Plasman na rang listi",
                "Naredna nedelja sačuvan plasman: 142. na globalnoj rang listi.",
                "juče",
                false,
                "rank_detail"));
        list.add(new InAppNotification(
                "3",
                InAppNotification.Category.REWARDS,
                "Nagrada dostupna",
                "Ostvaren si uslov za kutiju sa poklonima ovog meseca.",
                "pre 2 dana",
                true,
                "rewards_claim"));
        list.add(new InAppNotification(
                "4",
                InAppNotification.Category.OTHER,
                "Zahtev za prijateljstvo",
                "ana_me šalje zahtev.",
                "pre 1 h",
                false,
                "friend_accept"));
        list.add(new InAppNotification(
                "5",
                InAppNotification.Category.OTHER,
                "Nova liga",
                "Prešla si iz Srebrne u Zlatnu ligu.",
                "pre 20 min",
                false,
                "league_info"));
        return list;
    }

    private void attachFilterListeners() {
        Chip chipAll = binding.chipFilterAll;
        Chip chipUnread = binding.chipFilterUnread;
        Chip chipRead = binding.chipFilterRead;

        chipAll.setOnCheckedChangeListener((b, checked) -> {
            if (checked) {
                filterMode = FilterMode.ALL;
                refreshListUi();
            }
        });
        chipUnread.setOnCheckedChangeListener((b, checked) -> {
            if (checked) {
                filterMode = FilterMode.UNREAD;
                refreshListUi();
            }
        });
        chipRead.setOnCheckedChangeListener((b, checked) -> {
            if (checked) {
                filterMode = FilterMode.READ;
                refreshListUi();
            }
        });
    }

    private List<InAppNotification> filtered() {
        List<InAppNotification> out = new ArrayList<>();
        for (InAppNotification n : masterList) {
            switch (filterMode) {
                case READ:
                    if (n.read) {
                        out.add(n);
                    }
                    break;
                case UNREAD:
                    if (!n.read) {
                        out.add(n);
                    }
                    break;
                default:
                    out.add(n);
            }
        }
        return out;
    }

    private void refreshListUi() {
        List<InAppNotification> show = filtered();
        adapter.submitList(show);
        boolean empty = show.isEmpty();
        binding.notificationsEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.notificationsList.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onMarkRead(InAppNotification item) {
        if (!item.read) {
            item.read = true;
            Snackbar.make(binding.getRoot(), R.string.notif_marked_read, Snackbar.LENGTH_SHORT).show();
        }
        refreshListUi();
    }

    @Override
    public void onOpen(InAppNotification item) {
        if (!item.read) {
            item.read = true;
            refreshListUi();
        }

        Bundle args = targetArgsFor(item);
        Navigation.findNavController(binding.getRoot())
                .navigate(R.id.notificationTargetFragment, args);
    }

    private Bundle targetArgsFor(InAppNotification item) {
        Bundle args = new Bundle();
        String actionHint = item.actionHint != null ? item.actionHint : "";

        switch (actionHint) {
            case "chat_thread":
                args.putString(NotificationTargetFragment.ARG_TITLE, "Čet");
                args.putString(NotificationTargetFragment.ARG_SUBTITLE, "Poruke igrača iz tvog regiona");
                args.putString(NotificationTargetFragment.ARG_MESSAGE,
                        "Otvoren je čet jer je notifikacija vezana za pristiglu poruku.");
                break;
            case "rank_detail":
                args.putString(NotificationTargetFragment.ARG_TITLE, "Rang lista");
                args.putString(NotificationTargetFragment.ARG_SUBTITLE, "Nedeljni i mesečni plasman");
                args.putString(NotificationTargetFragment.ARG_MESSAGE,
                        "Otvorena je rang lista jer je notifikacija vezana za plasman i rangiranje.");
                break;
            case "rewards_claim":
                args.putString(NotificationTargetFragment.ARG_TITLE, "Nagrade");
                args.putString(NotificationTargetFragment.ARG_SUBTITLE, "Tokeni i pokloni za osvojeni plasman");
                args.putString(NotificationTargetFragment.ARG_MESSAGE,
                        "Otvoren je pregled nagrada jer je notifikacija vezana za dostupnu nagradu.");
                break;
            case "friend_accept":
                args.putString(NotificationTargetFragment.ARG_TITLE, "Prijatelji");
                args.putString(NotificationTargetFragment.ARG_SUBTITLE, "Zahtevi i lista prijatelja");
                args.putString(NotificationTargetFragment.ARG_MESSAGE,
                        "Otvoreni su prijatelji jer je notifikacija vezana za zahtev za prijateljstvo.");
                break;
            case "league_info":
                args.putString(NotificationTargetFragment.ARG_TITLE, "Liga");
                args.putString(NotificationTargetFragment.ARG_SUBTITLE, "Napredovanje kroz lige");
                args.putString(NotificationTargetFragment.ARG_MESSAGE,
                        "Otvoren je pregled lige jer je notifikacija vezana za promenu lige.");
                break;
            default:
                args.putString(NotificationTargetFragment.ARG_TITLE, "Obaveštenje");
                args.putString(NotificationTargetFragment.ARG_SUBTITLE, item.title);
                args.putString(NotificationTargetFragment.ARG_MESSAGE, item.message);
                break;
        }

        return args;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
