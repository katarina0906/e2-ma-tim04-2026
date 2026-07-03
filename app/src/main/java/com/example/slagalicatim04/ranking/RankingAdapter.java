package com.example.slagalicatim04.ranking;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalicatim04.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RankingAdapter extends RecyclerView.Adapter<RankingAdapter.RankingHolder> {
    private final String cycleType;
    private List<RankingEntry> items = new ArrayList<>();

    public RankingAdapter(String cycleType) {
        this.cycleType = cycleType;
    }

    public void submitList(List<RankingEntry> next) {
        items = next;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RankingHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ranking_row, parent, false);
        return new RankingHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RankingHolder holder, int position) {
        holder.bind(items.get(position), cycleType);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class RankingHolder extends RecyclerView.ViewHolder {
        private final TextView rank;
        private final TextView username;
        private final TextView league;
        private final TextView stars;

        RankingHolder(@NonNull View itemView) {
            super(itemView);
            rank = itemView.findViewById(R.id.ranking_row_rank);
            username = itemView.findViewById(R.id.ranking_row_username);
            league = itemView.findViewById(R.id.ranking_row_league);
            stars = itemView.findViewById(R.id.ranking_row_stars);
        }

        void bind(RankingEntry entry, String cycleType) {
            rank.setText(String.format(Locale.ROOT, "%d.", entry.rank));
            username.setText(entry.username);
            league.setText(entry.leagueIcon);
            stars.setText(String.format(Locale.ROOT, "%d %s · %d tokena",
                    entry.stars, "\u2605", rewardForRank(cycleType, entry.rank)));
        }

        private static int rewardForRank(String cycleType, int rank) {
            boolean monthly = RankingCycle.MONTHLY.equals(cycleType);
            if (rank == 1) return monthly ? 10 : 5;
            if (rank == 2) return monthly ? 6 : 3;
            if (rank == 3) return monthly ? 4 : 2;
            if (rank >= 4 && rank <= 10) return monthly ? 2 : 1;
            return 0;
        }
    }
}
