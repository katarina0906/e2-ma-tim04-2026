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

public class LoginFragment extends Fragment {

    public LoginFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_login, container, false);

        TextInputEditText identifierInput = view.findViewById(R.id.loginIdentifierInput);
        TextInputEditText passwordInput = view.findViewById(R.id.loginPasswordInput);
        AuthService authService = AuthService.getInstance(requireContext());

        view.findViewById(R.id.loginButton).setOnClickListener(v -> {
            String identifier = textOf(identifierInput);
            String password = textOf(passwordInput);
            new Thread(() -> {
                AuthResult<AuthUser> result = authService.login(identifier, password);
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), result.getMessage(), Toast.LENGTH_LONG).show();
                    if (result.isSuccess()) {
                        Navigation.findNavController(v).navigate(R.id.action_loginFragment_to_homeFragment);
                    }
                });
            }).start();
        });

        view.findViewById(R.id.openRegisterButton).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_loginFragment_to_registerFragment));

        view.findViewById(R.id.continueAsGuestButton).setOnClickListener(v -> {
            new Thread(() -> {
                AuthResult<AuthUser> result = authService.continueAsGuest();
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), result.getMessage(), Toast.LENGTH_LONG).show();
                    if (result.isSuccess()) {
                        Navigation.findNavController(v).navigate(R.id.action_loginFragment_to_homeFragment);
                    }
                });
            }).start();
        });

        view.findViewById(R.id.openResetPasswordButton).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_loginFragment_to_resetPasswordFragment));

        return view;
    }

    private String textOf(TextInputEditText input) {
        Editable text = input.getText();
        return text == null ? "" : text.toString();
    }
}
