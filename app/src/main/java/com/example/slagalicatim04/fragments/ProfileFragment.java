package com.example.slagalicatim04.fragments;

import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;

import com.example.slagalicatim04.R;
import com.example.slagalicatim04.auth.AuthResult;
import com.example.slagalicatim04.auth.AuthService;
import com.example.slagalicatim04.auth.AuthUser;
import com.example.slagalicatim04.auth.AvatarImageLoader;
import com.example.slagalicatim04.auth.DailyMissionService;
import com.example.slagalicatim04.friends.FriendQr;
import com.example.slagalicatim04.leagues.LeagueInfo;
import com.example.slagalicatim04.ranking.RankingRewardSynchronizer;
import com.example.slagalicatim04.regions.AvatarFrameStyler;
import com.example.slagalicatim04.regions.OpenStreetRegionMapStyler;
import com.example.slagalicatim04.regions.OpenStreetRegionResolver;
import com.example.slagalicatim04.regions.RegionInfo;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.Locale;

public class ProfileFragment extends Fragment {

    private TextView usernameText;
    private TextView emailText;
    private TextView regionText;
    private TextView regionMapTitle;
    private TextView tokensSummary;
    private TextView starsSummary;
    private TextView leagueSummary;
    private TextView dailyMissionProgress;
    private TextView dailyMissionReward;
    private TextView missionWinStatus;
    private TextView missionChatStatus;
    private TextView missionFriendlyStatus;
    private TextView missionTournamentStatus;
    private ImageView avatarImage;
    private ImageView friendQrImage;
    private View avatarFrame;
    private MapView regionMapView;
    private AuthService authService;
    private DailyMissionService dailyMissionService;
    private AuthUser currentUser;
    private final RankingRewardSynchronizer rewardSynchronizer = new RankingRewardSynchronizer();
    private final ActivityResultLauncher<String> avatarPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::uploadAvatar);

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        authService = AuthService.getInstance(requireContext());
        dailyMissionService = new DailyMissionService(FirebaseFirestore.getInstance());
        usernameText = view.findViewById(R.id.profileUsername);
        emailText = view.findViewById(R.id.profileEmail);
        regionText = view.findViewById(R.id.profileRegion);
        regionMapTitle = view.findViewById(R.id.profileRegionMapTitle);
        tokensSummary = view.findViewById(R.id.profileTokensSummary);
        starsSummary = view.findViewById(R.id.profileStarsSummary);
        leagueSummary = view.findViewById(R.id.profileLeagueSummary);
        dailyMissionProgress = view.findViewById(R.id.profileDailyMissionProgress);
        dailyMissionReward = view.findViewById(R.id.profileDailyMissionReward);
        missionWinStatus = view.findViewById(R.id.profileMissionWinStatus);
        missionChatStatus = view.findViewById(R.id.profileMissionChatStatus);
        missionFriendlyStatus = view.findViewById(R.id.profileMissionFriendlyStatus);
        missionTournamentStatus = view.findViewById(R.id.profileMissionTournamentStatus);
        avatarImage = view.findViewById(R.id.profileAvatar);
        friendQrImage = view.findViewById(R.id.profileFriendQr);
        avatarFrame = view.findViewById(R.id.profileAvatarFrame);
        regionMapView = view.findViewById(R.id.profileRegionMap);
        OpenStreetRegionMapStyler.configure(requireContext(), regionMapView, 7.2);
        View changePasswordForm = view.findViewById(R.id.changePasswordForm);
        MaterialButton toggleChangePasswordButton = view.findViewById(R.id.toggleChangePasswordButton);
        TextInputEditText oldPasswordInput = view.findViewById(R.id.profileOldPasswordInput);
        TextInputEditText newPasswordInput = view.findViewById(R.id.profileNewPasswordInput);
        TextInputEditText confirmPasswordInput = view.findViewById(R.id.profileConfirmPasswordInput);

        view.findViewById(R.id.notifications_card).setOnClickListener(v ->
                Navigation.findNavController(requireActivity(), R.id.nav_host_fragment_content_main)
                        .navigate(R.id.action_profileFragment_to_notificationsFragment));
        view.findViewById(R.id.logoutButton).setOnClickListener(this::logout);
        view.findViewById(R.id.changeAvatarButton)
                .setOnClickListener(v -> avatarPicker.launch("image/*"));
        view.findViewById(R.id.editAvatarIcon)
                .setOnClickListener(v -> avatarPicker.launch("image/*"));
        toggleChangePasswordButton.setOnClickListener(v -> togglePasswordForm(changePasswordForm, toggleChangePasswordButton));
        view.findViewById(R.id.saveProfilePasswordButton).setOnClickListener(v ->
                changePassword(
                        changePasswordForm,
                        toggleChangePasswordButton,
                        oldPasswordInput,
                        newPasswordInput,
                        confirmPasswordInput
                ));

        currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            navigateToLogin(view);
            return;
        }
        showProfile(currentUser);
        loadProfileCounters(currentUser.getId());
        loadDailyMissions(currentUser.getId());
        loadProfileFromFirestore();
    }

    private void loadProfileFromFirestore() {
        new Thread(() -> {
            AuthResult<AuthUser> result = authService.refreshCurrentUser();
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) {
                    return;
                }
                if (result.isSuccess() && result.getData() != null) {
                    currentUser = result.getData();
                    showProfile(result.getData());
                    loadProfileCounters(result.getData().getId());
                    loadDailyMissions(result.getData().getId());
                } else {
                    Toast.makeText(requireContext(), result.getMessage(), Toast.LENGTH_LONG).show();
                    navigateToLogin(requireView());
                }
            });
        }).start();
    }

    private void showProfile(AuthUser user) {
        usernameText.setText(user.getUsername());
        emailText.setText(user.getEmail());
        regionText.setText(user.getRegion());
        showRegionMap(user);
        AvatarFrameStyler.apply(avatarFrame, user.getAvatarFramePlace());
        AvatarImageLoader.load(avatarImage, user.getAvatarData());
        showProgress(user);
        showFriendQr(user);
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
                    String leagueName = text(snapshot.getString("league"), "Nulta liga");
                    tokensSummary.setText(String.format(Locale.ROOT, "%d", tokens));
                    starsSummary.setText(String.format(Locale.ROOT, "%d", stars));
                    leagueSummary.setText(leagueName);
                });
    }

    private void loadDailyMissions(String userId) {
        dailyMissionService.loadStatus(userId, new DailyMissionService.StatusListener() {
            @Override
            public void onStatus(DailyMissionService.Status status) {
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> showDailyMissions(status));
            }

            @Override
            public void onError(Exception error) {
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> {
                    dailyMissionProgress.setText("Misije trenutno nisu dostupne.");
                    dailyMissionReward.setText("...");
                });
            }
        });
    }

    private void showDailyMissions(DailyMissionService.Status status) {
        dailyMissionProgress.setText(status.completedCount() + "/4 zavrseno danas");
        dailyMissionReward.setText(status.allBonusClaimed ? "Bonus uzet" : "+3 po misiji");
        dailyMissionReward.setTextColor(status.allBonusClaimed ? 0xFFFFFFFF : 0xFF6F4BB2);
        dailyMissionReward.setBackgroundResource(status.allBonusClaimed
                ? R.drawable.bg_daily_mission_done
                : R.drawable.bg_daily_mission_pending);
        bindMissionStatus(missionWinStatus,
                status.isCompleted(DailyMissionService.Mission.WIN_MATCH));
        bindMissionStatus(missionChatStatus,
                status.isCompleted(DailyMissionService.Mission.SEND_CHAT_MESSAGE));
        bindMissionStatus(missionFriendlyStatus,
                status.isCompleted(DailyMissionService.Mission.PLAY_FRIENDLY_MATCH));
        bindMissionStatus(missionTournamentStatus,
                status.isCompleted(DailyMissionService.Mission.WIN_TOURNAMENT_MATCH));
    }

    private void bindMissionStatus(TextView statusView, boolean completed) {
        statusView.setText(completed ? "Gotovo" : "+3 zvezde");
        statusView.setTextColor(completed ? 0xFFFFFFFF : 0xFF6F4BB2);
        statusView.setBackgroundResource(completed
                ? R.drawable.bg_daily_mission_done
                : R.drawable.bg_daily_mission_pending);
    }

    private void showProgress(AuthUser user) {
        LeagueInfo league = LeagueInfo.forStars(user.getTotalStars());
        tokensSummary.setText(String.valueOf(user.getTokens()));
        starsSummary.setText(String.valueOf(user.getTotalStars()));
        leagueSummary.setText(league.name);
        leagueSummary.setCompoundDrawablesWithIntrinsicBounds(0, league.iconRes, 0, 0);
        leagueSummary.setCompoundDrawablePadding(4);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (regionMapView != null) {
            regionMapView.onResume();
        }
    }

    @Override
    public void onPause() {
        if (regionMapView != null) {
            regionMapView.onPause();
        }
        super.onPause();
    }

    private void uploadAvatar(Uri imageUri) {
        if (imageUri == null) {
            return;
        }
        avatarImage.setPadding(0, 0, 0, 0);
        avatarImage.setImageURI(imageUri);
        Toast.makeText(requireContext(), "Cuvanje profilne slike...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            AuthResult<AuthUser> result = authService.updateAvatar(imageUri);
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(requireContext(), result.getMessage(), Toast.LENGTH_LONG).show();
                if (result.isSuccess() && result.getData() != null) {
                    currentUser = result.getData();
                    showProfile(result.getData());
                }
            });
        }).start();
    }

    private void togglePasswordForm(View form, MaterialButton toggleButton) {
        boolean show = form.getVisibility() != View.VISIBLE;
        form.setVisibility(show ? View.VISIBLE : View.GONE);
        toggleButton.setText(show
                ? R.string.profile_change_password_toggle_close
                : R.string.profile_change_password_toggle_open);
    }

    private void changePassword(View form,
                                MaterialButton toggleButton,
                                TextInputEditText oldPasswordInput,
                                TextInputEditText newPasswordInput,
                                TextInputEditText confirmPasswordInput) {
        if (currentUser == null) {
            Toast.makeText(requireContext(), "Korisnik nije prijavljen.", Toast.LENGTH_LONG).show();
            navigateToLogin(requireView());
            return;
        }

        String oldPassword = textOf(oldPasswordInput);
        String newPassword = textOf(newPasswordInput);
        String confirmPassword = textOf(confirmPasswordInput);

        new Thread(() -> {
            AuthResult<AuthUser> result = authService.changePassword(
                    currentUser.getEmail(),
                    oldPassword,
                    newPassword,
                    confirmPassword
            );
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(requireContext(), result.getMessage(), Toast.LENGTH_LONG).show();
                if (result.isSuccess()) {
                    oldPasswordInput.setText("");
                    newPasswordInput.setText("");
                    confirmPasswordInput.setText("");
                    form.setVisibility(View.GONE);
                    toggleButton.setText(R.string.profile_change_password_toggle_open);
                }
            });
        }).start();
    }

    private void logout(View view) {
        authService.logout();
        Toast.makeText(requireContext(), "Uspesno ste se odjavili.", Toast.LENGTH_SHORT).show();
        navigateToLogin(view);
    }

    private void navigateToLogin(View view) {
        NavController navController = Navigation.findNavController(view);
        NavOptions options = new NavOptions.Builder()
                .setPopUpTo(R.id.nav_graph, true)
                .build();
        navController.navigate(R.id.loginFragment, null, options);
    }

    private String textOf(TextInputEditText input) {
        Editable text = input.getText();
        return text == null ? "" : text.toString();
    }

    private void showRegionMap(AuthUser user) {
        RegionInfo region = RegionInfo.byName(user.getRegion());
        regionMapTitle.setText("Moj region: " + region.name);
        regionMapView.getOverlays().clear();
        OpenStreetRegionMapStyler.addRegionOverlays(regionMapView, region.key);
        OpenStreetRegionMapStyler.focusRegion(regionMapView, region, 7.6);

        double[] fallbackLocation = OpenStreetRegionResolver.centerForRegion(region);
        double latitude = user.getRegionMapLatitude() == null
                ? fallbackLocation[0] : user.getRegionMapLatitude();
        double longitude = user.getRegionMapLongitude() == null
                ? fallbackLocation[1] : user.getRegionMapLongitude();

        Marker marker = new Marker(regionMapView);
        marker.setPosition(new GeoPoint(latitude, longitude));
        marker.setTitle(user.getUsername());
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        regionMapView.getOverlays().add(marker);
        regionMapView.invalidate();
    }

    private void showFriendQr(AuthUser user) {
        try {
            friendQrImage.setImageBitmap(FriendQr.bitmapForUser(user.getId(), 420));
        } catch (Exception error) {
            friendQrImage.setImageResource(android.R.drawable.ic_menu_share);
        }
    }

    private static long value(Long value) {
        return value == null ? 0 : value;
    }

    private static String text(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
