package com.example.slagalicatim04.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.slagalicatim04.databinding.FragmentRankingBinding;
import com.example.slagalicatim04.ranking.RankingAdapter;
import com.example.slagalicatim04.ranking.RankingCycle;
import com.example.slagalicatim04.ranking.RankingRepository;

public class RankingFragment extends Fragment {
    private static final long REFRESH_INTERVAL_MS = 120_000L;

    private FragmentRankingBinding binding;
    private final RankingRepository repository = new RankingRepository();
    private final RankingAdapter weeklyAdapter = new RankingAdapter(RankingCycle.WEEKLY);
    private final RankingAdapter monthlyAdapter = new RankingAdapter(RankingCycle.MONTHLY);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            loadRankings();
            handler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRankingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.weeklyRankingList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.weeklyRankingList.setAdapter(weeklyAdapter);
        binding.monthlyRankingList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.monthlyRankingList.setAdapter(monthlyAdapter);
    }

    @Override
    public void onStart() {
        super.onStart();
        refreshRunnable.run();
    }

    @Override
    public void onStop() {
        super.onStop();
        handler.removeCallbacks(refreshRunnable);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void loadRankings() {
        load(RankingCycle.WEEKLY, binding.weeklyCycleRange, weeklyAdapter, binding.weeklyEmpty);
        load(RankingCycle.MONTHLY, binding.monthlyCycleRange, monthlyAdapter, binding.monthlyEmpty);
    }

    private void load(String type, TextView range, RankingAdapter adapter, TextView empty) {
        repository.loadCurrent(type, new RankingRepository.RankingListener() {
            @Override
            public void onRanking(RankingCycle cycle, java.util.List<com.example.slagalicatim04.ranking.RankingEntry> entries) {
                if (binding == null) return;
                range.setText(cycle.label());
                adapter.submitList(entries);
                empty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(Exception error) {
                if (binding == null) return;
                empty.setText("Rang lista trenutno nije dostupna.");
                empty.setVisibility(View.VISIBLE);
            }
        });
    }
}
