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

import com.example.slagalicatim04.databinding.FragmentNotificationTargetBinding;
import com.example.slagalicatim04.notifications.NotificationRouter;

public class NotificationTargetFragment extends Fragment {

    public static final String ARG_TITLE = "targetTitle";
    public static final String ARG_SUBTITLE = "targetSubtitle";
    public static final String ARG_MESSAGE = "targetMessage";
    public static final String ARG_ACTION = "targetAction";
    public static final String ARG_TARGET_ID = "targetId";

    private FragmentNotificationTargetBinding binding;

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
        if (NotificationRouter.ACTION_REWARD.equals(args.getString(ARG_ACTION, ""))) {
            showRewardCelebration();
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
}
