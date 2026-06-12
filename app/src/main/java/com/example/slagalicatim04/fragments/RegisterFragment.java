package com.example.slagalicatim04.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
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

public class RegisterFragment extends Fragment {

    public RegisterFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_register, container, false);

        TextInputEditText emailInput = view.findViewById(R.id.registerEmailInput);
        TextInputEditText usernameInput = view.findViewById(R.id.registerUsernameInput);
        AutoCompleteTextView regionInput = view.findViewById(R.id.registerRegionInput);
        TextInputEditText passwordInput = view.findViewById(R.id.registerPasswordInput);
        TextInputEditText confirmPasswordInput = view.findViewById(R.id.registerConfirmPasswordInput);
        AuthService authService = AuthService.getInstance(requireContext());
        ArrayAdapter<CharSequence> regionAdapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.serbia_regions,
                android.R.layout.simple_list_item_1
        );
        regionInput.setAdapter(regionAdapter);

        view.findViewById(R.id.registerButton).setOnClickListener(v -> {
            String email = textOf(emailInput);
            String username = textOf(usernameInput);
            String region = textOf(regionInput);
            String password = textOf(passwordInput);
            String confirmPassword = textOf(confirmPasswordInput);
            new Thread(() -> {
                AuthResult<AuthUser> result = authService.register(
                        email,
                        username,
                        region,
                        password,
                        confirmPassword
                );
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), result.getMessage(), Toast.LENGTH_LONG).show();
                    if (result.isSuccess()) {
                        Bundle arguments = new Bundle();
                        arguments.putString("identifier", result.getData().getEmail());
                        Navigation.findNavController(v)
                                .navigate(R.id.action_registerFragment_to_emailVerificationFragment, arguments);
                    }
                });
            }).start();
        });

        view.findViewById(R.id.backToLoginFromRegisterButton).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_registerFragment_to_loginFragment));

        return view;
    }

    private String textOf(android.widget.TextView textView) {
        Editable text = textView.getEditableText();
        return text == null ? "" : text.toString();
    }
}
