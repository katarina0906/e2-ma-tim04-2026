package com.example.slagalicatim04.fragments;

import android.os.Bundle;
import android.media.AudioManager;
import android.media.ToneGenerator;
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
        ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70);
        tone.startTone(ToneGenerator.TONE_PROP_ACK, 180);
        binding.rewardIcon.postDelayed(tone::release, 400);
    }
}
