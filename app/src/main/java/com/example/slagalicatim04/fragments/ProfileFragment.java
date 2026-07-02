package com.example.slagalicatim04.fragments;

import android.os.Bundle;
import android.net.Uri;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;

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
import com.example.slagalicatim04.ranking.RankingRewardSynchronizer;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

public class ProfileFragment extends Fragment {

    private TextView usernameText;
    private TextView emailText;
    private TextView regionText;
    private TextView tokensText;
    private TextView starsText;
    private TextView leagueText;
    private ImageView avatarImage;
    private AuthService authService;
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
        usernameText = view.findViewById(R.id.profileUsername);
        emailText = view.findViewById(R.id.profileEmail);
        regionText = view.findViewById(R.id.profileRegion);
        tokensText = view.findViewById(R.id.profileTokens);
        starsText = view.findViewById(R.id.profileStars);
        leagueText = view.findViewById(R.id.profileLeague);
        avatarImage = view.findViewById(R.id.profileAvatar);
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
                    leagueText.setText(leagueText(leagueIcon, leagueName));
                });
    }

    private static String leagueText(String leagueIcon, String leagueName) {
        String[] words = leagueName.split("\\s+", 2);
        String first = words.length > 0 ? words[0] : leagueName;
        String second = words.length > 1 ? words[1] : "";
        return leagueIcon + "\n" + first + "\n" + second;
    }

    private static long value(Long value) {
        return value == null ? 0 : value;
    }

    private static String text(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
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
}
