package com.example.slagalicatim04.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.slagalicatim04.R;

public class ResetPasswordFragment extends Fragment {

    public ResetPasswordFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_reset_password, container, false);

        view.findViewById(R.id.saveNewPasswordButton).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_resetPasswordFragment_to_loginFragment));

        view.findViewById(R.id.backToLoginFromResetButton).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_resetPasswordFragment_to_loginFragment));

        return view;
    }
}
