package com.example.slagalicatim04.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.slagalicatim04.databinding.FragmentNotificationTargetBinding;

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
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
