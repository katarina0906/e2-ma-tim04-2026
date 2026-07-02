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
import com.example.slagalicatim04.ranking.RankingRewardSynchronizer;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

public class HomeFragment extends Fragment {

    private TextView usernameText;
    private TextView regionText;
    private TextView tokensText;
    private TextView starsText;
    private TextView leagueText;
    private ImageView avatarImage;
    private AuthService authService;
    private final RankingRewardSynchronizer rewardSynchronizer = new RankingRewardSynchronizer();

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
        tokensText = view.findViewById(R.id.homeProfileTokens);
        starsText = view.findViewById(R.id.homeProfileStars);
        leagueText = view.findViewById(R.id.homeProfileLeague);
        avatarImage = view.findViewById(R.id.homeProfileAvatar);

        view.findViewById(R.id.homeProfileCard).setOnClickListener(v -> {
            BottomNavigationView bnv = requireActivity().findViewById(R.id.bottom_navigation);
            bnv.setSelectedItemId(R.id.profileFragment);
        });
        view.findViewById(R.id.homeQuickNotifications).setOnClickListener(v ->
                Navigation.findNavController(requireActivity(), R.id.nav_host_fragment_content_main)
                        .navigate(R.id.action_homeFragment_to_notificationsFragment));
        view.findViewById(R.id.homeQuickRanking).setOnClickListener(v ->
                Navigation.findNavController(requireActivity(), R.id.nav_host_fragment_content_main)
                        .navigate(R.id.action_homeFragment_to_rankingFragment));
        view.findViewById(R.id.startGameCard).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.stepByStepWaitingRoomFragment));

        AuthUser user = authService.getCurrentUser();
        if (user != null) {
            showProfile(user);
            loadProfileCounters(user.getId());
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
                    loadProfileCounters(result.getData().getId());
                }
            });
        }).start();
    }

    private void showProfile(AuthUser user) {
        usernameText.setText(user.getUsername());
        regionText.setText(user.getRegion());
        AvatarImageLoader.load(avatarImage, user.getAvatarData());
    }

    private void loadProfileCounters(String userId) {
        if (userId == null || userId.isEmpty() || FirebaseAuth.getInstance().getCurrentUser() == null) {
            return;
        }
        rewardSynchronizer.syncCurrentRewards(userId, () -> readProfileCounters(userId));
    }

    private void readProfileCounters(String userId) {
        FirebaseFirestore.getInstance().collection("users").document(userId).get()
                .addOnSuccessListener(snapshot -> {
                    if (!isAdded()) {
                        return;
                    }
                    long tokens = value(snapshot.getLong("tokens"));
                    long stars = value(snapshot.getLong("totalStars"));
                    String leagueIcon = text(snapshot.getString("leagueIcon"), "🏆");
                    String leagueName = text(snapshot.getString("leagueName"), "Zlatna liga");
                    tokensText.setText(String.format(Locale.ROOT, "🪙\n%d\nTokena", tokens));
                    starsText.setText(String.format(Locale.ROOT, "%s\n%d\nZvezda", "\u2605", stars));
                    leagueText.setText(leagueIcon + " " + leagueName);
                });
    }

    private static long value(Long value) {
        return value == null ? 0 : value;
    }

    private static String text(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
