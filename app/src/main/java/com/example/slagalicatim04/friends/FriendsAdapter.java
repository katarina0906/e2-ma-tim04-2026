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

import java.util.ArrayList;
import java.util.List;

public class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.Holder> {
    private final List<FriendItem> items = new ArrayList<>();

    public void submit(List<FriendItem> friends) {
        items.clear();
        if (friends != null) {
            items.addAll(friends);
        }
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
        AvatarFrameStyler.apply(holder.avatarFrame, friend.avatarFramePlace);
        AvatarImageLoader.load(holder.avatar, friend.avatarData);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final View avatarFrame;
        final ImageView avatar;
        final TextView username;
        final TextView region;
        final TextView email;

        Holder(@NonNull View itemView) {
            super(itemView);
            avatarFrame = itemView.findViewById(R.id.friendAvatarFrame);
            avatar = itemView.findViewById(R.id.friendAvatar);
            username = itemView.findViewById(R.id.friendUsername);
            region = itemView.findViewById(R.id.friendRegion);
            email = itemView.findViewById(R.id.friendEmail);
        }
    }
}
