package com.example.slagalicatim04.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.slagalicatim04.R;
import com.example.slagalicatim04.auth.AuthResult;
import com.example.slagalicatim04.auth.AuthService;
import com.example.slagalicatim04.auth.AuthUser;
import com.google.android.material.textfield.TextInputEditText;

public class ResetPasswordFragment extends Fragment {

    public ResetPasswordFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_reset_password, container, false);

        TextInputEditText identifierInput = view.findViewById(R.id.resetIdentifierInput);
        TextInputEditText oldPasswordInput = view.findViewById(R.id.resetOldPasswordInput);
        TextInputEditText newPasswordInput = view.findViewById(R.id.resetNewPasswordInput);
        TextInputEditText confirmPasswordInput = view.findViewById(R.id.resetConfirmPasswordInput);
        AuthService authService = AuthService.getInstance(requireContext());

        view.findViewById(R.id.saveNewPasswordButton).setOnClickListener(v -> {
            String identifier = textOf(identifierInput);
            String oldPassword = textOf(oldPasswordInput);
            String newPassword = textOf(newPasswordInput);
            String confirmPassword = textOf(confirmPasswordInput);
            new Thread(() -> {
                AuthResult<AuthUser> result = authService.changePassword(
                        identifier,
                        oldPassword,
                        newPassword,
                        confirmPassword
                );
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), result.getMessage(), Toast.LENGTH_LONG).show();
                    if (result.isSuccess()) {
                        Navigation.findNavController(v).navigate(R.id.action_resetPasswordFragment_to_loginFragment);
                    }
                });
            }).start();
        });

        view.findViewById(R.id.backToLoginFromResetButton).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_resetPasswordFragment_to_loginFragment));

        return view;
    }

    private String textOf(TextInputEditText input) {
        Editable text = input.getText();
        return text == null ? "" : text.toString();
    }
}
