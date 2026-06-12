package com.example.slagalicatim04.fragments;

import android.os.Bundle;
import android.net.Uri;
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

public class ProfileFragment extends Fragment {

    private TextView usernameText;
    private TextView emailText;
    private TextView regionText;
    private ImageView avatarImage;
    private AuthService authService;
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
        avatarImage = view.findViewById(R.id.profileAvatar);

        view.findViewById(R.id.notifications_card).setOnClickListener(v ->
                Navigation.findNavController(requireActivity(), R.id.nav_host_fragment_content_main)
                        .navigate(R.id.action_profileFragment_to_notificationsFragment));
        view.findViewById(R.id.logoutButton).setOnClickListener(this::logout);
        view.findViewById(R.id.changeAvatarButton)
                .setOnClickListener(v -> avatarPicker.launch("image/*"));
        view.findViewById(R.id.editAvatarIcon)
                .setOnClickListener(v -> avatarPicker.launch("image/*"));

        AuthUser cachedUser = authService.getCurrentUser();
        if (cachedUser == null) {
            navigateToLogin(view);
            return;
        }
        showProfile(cachedUser);
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
                    showProfile(result.getData());
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
        AvatarImageLoader.load(avatarImage, user.getAvatarUrl());
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
                    showProfile(result.getData());
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
}
