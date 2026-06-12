package com.example.slagalicatim04.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.slagalicatim04.R;
import com.example.slagalicatim04.auth.AuthResult;
import com.example.slagalicatim04.auth.AuthService;
import com.example.slagalicatim04.auth.AuthUser;
import com.example.slagalicatim04.auth.AvatarImageLoader;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class HomeFragment extends Fragment {

    private TextView usernameText;
    private TextView regionText;
    private ImageView avatarImage;
    private AuthService authService;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        authService = AuthService.getInstance(requireContext());
        usernameText = view.findViewById(R.id.homeProfileUsername);
        regionText = view.findViewById(R.id.homeProfileRegion);
        avatarImage = view.findViewById(R.id.homeProfileAvatar);

        view.findViewById(R.id.homeProfileCard).setOnClickListener(v -> {
            BottomNavigationView bnv = requireActivity().findViewById(R.id.bottom_navigation);
            bnv.setSelectedItemId(R.id.profileFragment);
        });
        view.findViewById(R.id.homeQuickNotifications).setOnClickListener(v ->
                Navigation.findNavController(requireActivity(), R.id.nav_host_fragment_content_main)
                        .navigate(R.id.action_homeFragment_to_notificationsFragment));

        AuthUser user = authService.getCurrentUser();
        if (user != null) {
            showProfile(user);
        }
        refreshProfile();
    }

    private void refreshProfile() {
        new Thread(() -> {
            AuthResult<AuthUser> result = authService.refreshCurrentUser();
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                if (isAdded() && result.isSuccess() && result.getData() != null) {
                    showProfile(result.getData());
                }
            });
        }).start();
    }

    private void showProfile(AuthUser user) {
        usernameText.setText(user.getUsername());
        regionText.setText(user.getRegion());
        AvatarImageLoader.load(avatarImage, user.getAvatarData());
    }
}
