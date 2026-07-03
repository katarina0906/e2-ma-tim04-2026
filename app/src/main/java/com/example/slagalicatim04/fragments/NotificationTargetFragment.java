package com.example.slagalicatim04.fragments;

import android.os.Bundle;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.slagalicatim04.databinding.FragmentNotificationTargetBinding;
import com.example.slagalicatim04.notifications.NotificationRouter;
import com.example.slagalicatim04.ranking.RankingAdapter;
import com.example.slagalicatim04.ranking.RankingCycle;
import com.example.slagalicatim04.ranking.RankingEntry;
import com.example.slagalicatim04.ranking.RankingRepository;
import com.google.firebase.auth.FirebaseAuth;

import java.util.List;
import java.util.Locale;

public class NotificationTargetFragment extends Fragment {

    public static final String ARG_TITLE = "targetTitle";
    public static final String ARG_SUBTITLE = "targetSubtitle";
    public static final String ARG_MESSAGE = "targetMessage";
    public static final String ARG_ACTION = "targetAction";
    public static final String ARG_TARGET_ID = "targetId";

    private FragmentNotificationTargetBinding binding;
    private RankingAdapter rankingAdapter;
    private final RankingRepository rankingRepository = new RankingRepository();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentNotificationTargetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        if (args == null) {
            return;
        }

        binding.targetTitle.setText(args.getString(ARG_TITLE, ""));
        binding.targetSubtitle.setText(args.getString(ARG_SUBTITLE, ""));
        binding.targetMessage.setText(args.getString(ARG_MESSAGE, ""));
        String action = args.getString(ARG_ACTION, "");
        if (NotificationRouter.ACTION_REWARD.equals(action)) {
            showRewardCelebration();
        } else if (NotificationRouter.ACTION_RANKING.equals(action)) {
            binding.targetMessage.setVisibility(View.GONE);
            showRankingDetails(args.getString(ARG_TARGET_ID, ""));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void showRewardCelebration() {
        binding.rewardCelebration.setVisibility(View.VISIBLE);
        binding.fullScreenConfetti.burst();
        binding.rewardIcon.setScaleX(0.4f);
        binding.rewardIcon.setScaleY(0.4f);
        binding.rewardIcon.setRotation(-18f);
        binding.rewardIcon.animate()
                .scaleX(1.18f)
                .scaleY(1.18f)
                .rotation(12f)
                .setDuration(360)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> binding.rewardIcon.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .rotation(0f)
                        .setDuration(220)
                        .start())
                .start();
        playRewardSound();
    }

    private void playRewardSound() {
        new Thread(() -> {
            final int sampleRate = 44100;
            short[] samples = melodySamples(sampleRate);
            AudioTrack track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(samples.length * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            track.write(samples, 0, samples.length);
            track.play();
            try {
                Thread.sleep(900);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            track.release();
        }).start();
    }

    private short[] melodySamples(int sampleRate) {
        double[] notes = {523.25, 659.25, 783.99, 1046.50};
        int noteMs = 150;
        int gapMs = 28;
        int totalSamples = (noteMs + gapMs) * notes.length * sampleRate / 1000;
        short[] out = new short[totalSamples];
        int offset = 0;
        for (double note : notes) {
            int noteSamples = noteMs * sampleRate / 1000;
            int gapSamples = gapMs * sampleRate / 1000;
            for (int i = 0; i < noteSamples && offset + i < out.length; i++) {
                double t = i / (double) sampleRate;
                double envelope = Math.sin(Math.PI * i / noteSamples);
                double shimmer = Math.sin(2.0 * Math.PI * note * 2.0 * t) * 0.18;
                double wave = Math.sin(2.0 * Math.PI * note * t) * 0.72 + shimmer;
                out[offset + i] = (short) (wave * envelope * 15000);
            }
            offset += noteSamples + gapSamples;
        }
        return out;
    }

    private void showRankingDetails(String targetId) {
        String cycleType = cycleTypeFromTarget(targetId);
        rankingAdapter = new RankingAdapter(cycleType);
        binding.rankingDetails.setVisibility(View.VISIBLE);
        binding.rankingFullList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rankingFullList.setAdapter(rankingAdapter);
        binding.rankingPosition.setText("Ucitavanje plasmana...");
        binding.rankingEmpty.setVisibility(View.GONE);

        rankingRepository.loadCurrent(cycleType, new RankingRepository.RankingListener() {
            @Override
            public void onRanking(RankingCycle cycle, List<RankingEntry> entries) {
                if (binding == null) {
                    return;
                }
                binding.rankingCycleRange.setText(cycle.label());
                rankingAdapter.submitList(entries);
                binding.rankingEmpty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
                binding.rankingPosition.setText(positionText(cycleType, entries));
            }

            @Override
            public void onError(Exception error) {
                if (binding == null) {
                    return;
                }
                binding.rankingPosition.setText("Rang lista trenutno nije dostupna.");
                binding.rankingEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    private String positionText(String cycleType, List<RankingEntry> entries) {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() == null
                ? "" : FirebaseAuth.getInstance().getCurrentUser().getUid();
        for (RankingEntry entry : entries) {
            if (entry.userId.equals(currentUserId)) {
                return String.format(Locale.ROOT, "Trenutno si %d. na %s rang listi sa %d zvezda.",
                        entry.rank, cycleLabel(cycleType), entry.stars);
            }
        }
        if (entries.isEmpty()) {
            return "Jos nema igraca u ovom ciklusu.";
        }
        return "Nisi jos rangiran/a u ovom ciklusu. Odigraj bar jednu partiju.";
    }

    private String cycleTypeFromTarget(String targetId) {
        String normalized = targetId == null ? "" : targetId.toLowerCase(Locale.ROOT);
        return normalized.contains("monthly") || normalized.contains("mesec")
                ? RankingCycle.MONTHLY
                : RankingCycle.WEEKLY;
    }

    private String cycleLabel(String cycleType) {
        return RankingCycle.MONTHLY.equals(cycleType) ? "mesecnoj" : "nedeljnoj";
    }
}
