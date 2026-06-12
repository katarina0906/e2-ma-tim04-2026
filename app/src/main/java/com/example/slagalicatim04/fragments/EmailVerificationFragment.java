package com.example.slagalicatim04.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

public class EmailVerificationFragment extends Fragment {

    public EmailVerificationFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_email_verification, container, false);

        AuthService authService = AuthService.getInstance(requireContext());
        TextView sentToText = view.findViewById(R.id.verificationSentToText);
        TextView statusText = view.findViewById(R.id.verificationStatusText);
        String identifier = getArguments() == null ? "" : getArguments().getString("identifier", "");

        if (!identifier.isEmpty()) {
            sentToText.setText("Poslato na: " + identifier);
        }

        view.findViewById(R.id.backToLoginFromVerificationButton).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_emailVerificationFragment_to_loginFragment));

        view.findViewById(R.id.resendVerificationButton).setOnClickListener(v -> {
            new Thread(() -> {
                AuthResult<AuthUser> result = authService.resendVerification(identifier);
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), result.getMessage(), Toast.LENGTH_LONG).show();
                    statusText.setText(result.getMessage());
                });
            }).start();
        });

        return view;
    }
}
