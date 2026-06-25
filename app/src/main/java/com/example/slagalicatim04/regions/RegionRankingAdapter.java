package com.example.slagalicatim04.regions;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalicatim04.R;

import java.util.ArrayList;
import java.util.List;

public class RegionRankingAdapter extends RecyclerView.Adapter<RegionRankingAdapter.Holder> {
    private final List<RegionRankItem> items = new ArrayList<>();

    public void submit(List<RegionRankItem> nextItems) {
        items.clear();
        if (nextItems != null) {
            items.addAll(nextItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_region_rank, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        RegionRankItem item = items.get(position);
        holder.place.setText(String.valueOf(item.rank));
        holder.icon.setText(item.region.iconLabel);
        holder.name.setText(item.region.name);
        holder.stars.setText(item.monthlyStars + " zvezda");
        holder.itemView.setBackgroundColor(item.currentPlayerRegion ? 0xFFEDE7F6 : Color.WHITE);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView place;
        final TextView icon;
        final TextView name;
        final TextView stars;

        Holder(@NonNull View itemView) {
            super(itemView);
            place = itemView.findViewById(R.id.regionRankPlace);
            icon = itemView.findViewById(R.id.regionRankIcon);
            name = itemView.findViewById(R.id.regionRankName);
            stars = itemView.findViewById(R.id.regionRankStars);
        }
    }
}
