package com.example.slagalicatim04.regions;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalicatim04.R;

import java.util.ArrayList;
import java.util.List;

public class RegionChatAdapter extends RecyclerView.Adapter<RegionChatAdapter.ChatHolder> {

    private final List<RegionChatMessage> items = new ArrayList<>();
    private final String currentUserId;

    public RegionChatAdapter(String currentUserId) {
        this.currentUserId = currentUserId == null ? "" : currentUserId;
    }

    public void submit(List<RegionChatMessage> messages) {
        items.clear();
        if (messages != null) {
            items.addAll(messages);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChatHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_region_chat_message, parent, false);
        return new ChatHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatHolder holder, int position) {
        holder.bind(items.get(position), currentUserId);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class ChatHolder extends RecyclerView.ViewHolder {
        private final View leftBubble;
        private final View rightBubble;
        private final TextView leftSender;
        private final TextView leftMessage;
        private final TextView leftTime;
        private final TextView rightMessage;
        private final TextView rightTime;

        ChatHolder(@NonNull View itemView) {
            super(itemView);
            leftBubble = itemView.findViewById(R.id.chatLeftBubble);
            rightBubble = itemView.findViewById(R.id.chatRightBubble);
            leftSender = itemView.findViewById(R.id.chatLeftSender);
            leftMessage = itemView.findViewById(R.id.chatLeftMessage);
            leftTime = itemView.findViewById(R.id.chatLeftTime);
            rightMessage = itemView.findViewById(R.id.chatRightMessage);
            rightTime = itemView.findViewById(R.id.chatRightTime);
        }

        void bind(RegionChatMessage message, String currentUserId) {
            boolean mine = currentUserId.equals(message.getSenderId());
            leftBubble.setVisibility(mine ? View.GONE : View.VISIBLE);
            rightBubble.setVisibility(mine ? View.VISIBLE : View.GONE);
            String formattedTime = RegionChatTimeFormatter.format(message.getSentAt());
            if (mine) {
                rightMessage.setText(message.getText());
                rightTime.setText(formattedTime);
            } else {
                leftSender.setText(message.getSenderName());
                leftMessage.setText(message.getText());
                leftTime.setText(formattedTime);
            }
        }
    }
}
