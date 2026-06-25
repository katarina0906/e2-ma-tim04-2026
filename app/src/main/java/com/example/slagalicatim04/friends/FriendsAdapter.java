package com.example.slagalicatim04.friends;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalicatim04.R;
import com.example.slagalicatim04.auth.AvatarImageLoader;
import com.example.slagalicatim04.regions.AvatarFrameStyler;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.Holder> {
    public interface StartGameListener {
        void onStartGame(FriendItem friend);
    }

    public interface CancelInviteListener {
        void onCancelInvite(FriendItem friend);
    }

    private final List<FriendItem> items = new ArrayList<>();
    private final StartGameListener startGameListener;
    private final CancelInviteListener cancelInviteListener;
    private String pendingInviteFriendId = "";

    public FriendsAdapter(StartGameListener startGameListener,
                          CancelInviteListener cancelInviteListener) {
        this.startGameListener = startGameListener;
        this.cancelInviteListener = cancelInviteListener;
    }

    public void submit(List<FriendItem> friends) {
        items.clear();
        if (friends != null) {
            items.addAll(friends);
        }
        notifyDataSetChanged();
    }

    public void setPendingInviteFriendId(String friendId) {
        pendingInviteFriendId = friendId == null ? "" : friendId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_friend, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        FriendItem friend = items.get(position);
        holder.username.setText(friend.username);
        holder.region.setText(friend.region == null || friend.region.isEmpty()
                ? "Region nije izabran" : friend.region);
        holder.email.setText(friend.email);
        holder.monthlyRank.setText(friend.monthlyRank > 0
                ? "Mesecni rang: #" + friend.monthlyRank
                : "Mesecni rang: bez plasmana");
        holder.totalStars.setText("Ukupno zvezda: " + friend.totalStars);
        holder.league.setText("Liga: " + friend.league);
        holder.status.setText(statusText(friend));
        boolean invitePendingForThisFriend = friend.id.equals(pendingInviteFriendId);
        boolean anotherInvitePending = !pendingInviteFriendId.isEmpty() && !invitePendingForThisFriend;
        holder.startButton.setEnabled(invitePendingForThisFriend
                || (!anotherInvitePending && friend.canStartGame()));
        if (invitePendingForThisFriend) {
            holder.startButton.setText("Prekini zahtev");
        } else if (anotherInvitePending) {
            holder.startButton.setText("Zahtev je aktivan");
        } else {
            holder.startButton.setText(friend.canStartGame() ? "Zapocni partiju" : "Nije dostupan");
        }
        holder.startButton.setOnClickListener(v -> {
            if (invitePendingForThisFriend && cancelInviteListener != null) {
                cancelInviteListener.onCancelInvite(friend);
            } else if (startGameListener != null) {
                startGameListener.onStartGame(friend);
            }
        });
        AvatarFrameStyler.apply(holder.avatarFrame, friend.avatarFramePlace);
        AvatarImageLoader.load(holder.avatar, friend.avatarData);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String statusText(FriendItem friend) {
        if (friend.inGame) {
            return "Status: u partiji";
        }
        if (friend.online) {
            return "Status: ulogovan";
        }
        return "Status: offline";
    }

    static class Holder extends RecyclerView.ViewHolder {
        final View avatarFrame;
        final ImageView avatar;
        final TextView username;
        final TextView region;
        final TextView email;
        final TextView monthlyRank;
        final TextView totalStars;
        final TextView league;
        final TextView status;
        final MaterialButton startButton;

        Holder(@NonNull View itemView) {
            super(itemView);
            avatarFrame = itemView.findViewById(R.id.friendAvatarFrame);
            avatar = itemView.findViewById(R.id.friendAvatar);
            username = itemView.findViewById(R.id.friendUsername);
            region = itemView.findViewById(R.id.friendRegion);
            email = itemView.findViewById(R.id.friendEmail);
            monthlyRank = itemView.findViewById(R.id.friendMonthlyRank);
            totalStars = itemView.findViewById(R.id.friendTotalStars);
            league = itemView.findViewById(R.id.friendLeague);
            status = itemView.findViewById(R.id.friendStatus);
            startButton = itemView.findViewById(R.id.friendStartGameButton);
        }
    }
}
