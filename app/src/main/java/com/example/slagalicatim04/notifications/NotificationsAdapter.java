package com.example.slagalicatim04.notifications;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalicatim04.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class NotificationsAdapter extends RecyclerView.Adapter<NotificationsAdapter.NotifHolder> {

    public interface Listener {
        void onMarkRead(InAppNotification item);

        void onOpen(InAppNotification item);
    }

    private List<InAppNotification> items = new ArrayList<>();
    private final Listener listener;

    public NotificationsAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<InAppNotification> next) {
        items = next;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NotifHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View row = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification_row, parent, false);
        return new NotifHolder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull NotifHolder h, int position) {
        InAppNotification n = items.get(position);
        h.bind(n, listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class NotifHolder extends RecyclerView.ViewHolder {

        private final MaterialCardView card;
        private final View unreadDot;
        private final View categoryStripe;
        private final TextView categoryLabel;
        private final TextView title;
        private final TextView message;
        private final TextView time;
        private final MaterialButton markRead;
        private final MaterialButton open;

        NotifHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.notif_card);
            unreadDot = itemView.findViewById(R.id.notif_unread_dot);
            categoryStripe = itemView.findViewById(R.id.notif_category_stripe);
            categoryLabel = itemView.findViewById(R.id.notif_category_label);
            title = itemView.findViewById(R.id.notif_title);
            message = itemView.findViewById(R.id.notif_message);
            time = itemView.findViewById(R.id.notif_time);
            markRead = itemView.findViewById(R.id.notif_mark_read);
            open = itemView.findViewById(R.id.notif_open);
        }

        void bind(InAppNotification n, Listener listener) {
            title.setText(n.title);
            message.setText(n.message);
            time.setText(NotificationTimeFormatter.format(n.createdAt));
            categoryLabel.setText(categoryLabel.getContext().getString(n.category.labelRes));

            int stripeColorRes = stripeColorFor(n.category);
            int stripe = ContextCompat.getColor(categoryStripe.getContext(), stripeColorRes);
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(stripe);
            categoryStripe.setBackground(gd);

            boolean unread = !n.read;
            unreadDot.setVisibility(unread ? View.VISIBLE : View.GONE);
            float alphaCard = unread ? 1f : 0.88f;
            card.setAlpha(alphaCard);
            markRead.setVisibility(unread ? View.VISIBLE : View.GONE);

            card.setOnClickListener(v -> listener.onOpen(n));
            markRead.setOnClickListener(v -> listener.onMarkRead(n));
            open.setOnClickListener(v -> listener.onOpen(n));
        }

        private static int stripeColorFor(InAppNotification.Category c) {
            switch (c) {
                case CHAT:
                    return R.color.notif_cat_chat_stripe;
                case RANKING:
                    return R.color.notif_cat_ranking_stripe;
                case REWARDS:
                    return R.color.notif_cat_rewards_stripe;
                default:
                    return R.color.notif_cat_other_stripe;
            }
        }
    }
}
