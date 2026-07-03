package com.example.slagalicatim04.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.slagalicatim04.R;
import com.example.slagalicatim04.auth.AuthResult;
import com.example.slagalicatim04.auth.AuthService;
import com.example.slagalicatim04.auth.AuthUser;
import com.example.slagalicatim04.auth.AvatarImageLoader;
import com.example.slagalicatim04.leagues.LeagueInfo;
import com.example.slagalicatim04.ranking.RankingRewardSynchronizer;
import com.example.slagalicatim04.regions.AvatarFrameStyler;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

public class HomeFragment extends Fragment {
    public static final String GUEST_FRIENDLY_ROOM_ID = "friend_guest_public_room";

    private TextView usernameText;
    private TextView regionText;
    private TextView leagueText;
    private TextView tokensText;
    private TextView starsText;
    private ImageView avatarImage;
    private View avatarFrame;
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
        leagueText = view.findViewById(R.id.homeProfileLeague);
        tokensText = view.findViewById(R.id.homeProfileTokens);
        starsText = view.findViewById(R.id.homeProfileStars);
        avatarImage = view.findViewById(R.id.homeProfileAvatar);
        avatarFrame = view.findViewById(R.id.homeProfileAvatarFrame);

        view.findViewById(R.id.homeProfileCard).setOnClickListener(v ->
                openRegisteredOnly(v, R.id.profileFragment));
        view.findViewById(R.id.homeQuickNotifications).setOnClickListener(v ->
                openRegisteredOnly(v, R.id.action_homeFragment_to_notificationsFragment));
        view.findViewById(R.id.homeQuickRanking).setOnClickListener(v ->
                openRegisteredOnly(v, R.id.action_homeFragment_to_rankingFragment));
        view.findViewById(R.id.homeQuickFriends).setOnClickListener(v ->
                openRegisteredOnly(v, R.id.action_homeFragment_to_friendsFragment));
        view.findViewById(R.id.homeQuickTournament).setOnClickListener(v ->
                openRegisteredOnly(v, R.id.action_homeFragment_to_tournamentFragment));
        view.findViewById(R.id.startGameCard).setOnClickListener(v -> {
            AuthUser currentUser = authService.getCurrentUser();
            if (currentUser != null && currentUser.isGuest()) {
                Bundle args = new Bundle();
                args.putString("roomId", GUEST_FRIENDLY_ROOM_ID);
                Navigation.findNavController(v).navigate(R.id.stepByStepWaitingRoomFragment, args);
                return;
            }
            if (currentUser != null && currentUser.getTokens() < 1) {
                Toast.makeText(requireContext(), R.string.tokens_missing, Toast.LENGTH_LONG).show();
                return;
            }
            Navigation.findNavController(v).navigate(R.id.stepByStepWaitingRoomFragment);
        });

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
        regionText.setText(user.isGuest() ? "Prijateljska" : user.getRegion());
        if (user.isGuest()) {
            leagueText.setText("Gost");
            leagueText.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            tokensText.setText("0");
            starsText.setText("0");
            AvatarFrameStyler.apply(avatarFrame, user.getAvatarFramePlace());
            AvatarImageLoader.load(avatarImage, user.getAvatarData());
            return;
        }
        LeagueInfo league = LeagueInfo.forStars(user.getTotalStars());
        leagueText.setText(league.name);
        leagueText.setCompoundDrawablesWithIntrinsicBounds(league.iconRes, 0, 0, 0);
        leagueText.setCompoundDrawablePadding(6);
        tokensText.setText(String.valueOf(user.getTokens()));
        starsText.setText(String.valueOf(user.getTotalStars()));
        AvatarFrameStyler.apply(avatarFrame, user.getAvatarFramePlace());
        AvatarImageLoader.load(avatarImage, user.getAvatarData());
    }

    private void loadProfileCounters(String userId) {
        AuthUser currentUser = authService.getCurrentUser();
        if (currentUser != null && currentUser.isGuest()) {
            return;
        }
        if (userId == null || userId.isEmpty() || FirebaseAuth.getInstance().getCurrentUser() == null) {
            return;
        }
        rewardSynchronizer.syncCurrentRewards(userId, () -> readProfileCounters(userId));
    }

    private void openRegisteredOnly(View view, int destinationId) {
        AuthUser currentUser = authService.getCurrentUser();
        if (currentUser != null && currentUser.isGuest()) {
            Toast.makeText(requireContext(),
                    "Gost moze da igra samo prijateljsku partiju protiv drugog igraca.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Navigation.findNavController(view).navigate(destinationId);
    }

    private void readProfileCounters(String userId) {
        FirebaseFirestore.getInstance().collection("users").document(userId).get()
                .addOnSuccessListener(snapshot -> {
                    if (!isAdded()) {
                        return;
                    }
                    long tokens = value(snapshot.getLong("tokens"));
                    long stars = value(snapshot.getLong("totalStars"));
                    String leagueName = text(snapshot.getString("league"), "Nulta liga");
                    tokensText.setText(String.format(Locale.ROOT, "%d", tokens));
                    starsText.setText(String.format(Locale.ROOT, "%d", stars));
                    leagueText.setText(leagueName);
                });
    }

    private static long value(Long value) {
        return value == null ? 0 : value;
    }

    private static String text(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
