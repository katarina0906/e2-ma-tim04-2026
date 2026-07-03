package com.example.slagalicatim04.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.slagalicatim04.R;
import com.example.slagalicatim04.auth.AuthService;
import com.example.slagalicatim04.auth.AuthUser;

public class GamesFragment extends Fragment {

    public GamesFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_games, container, false);

        view.findViewById(R.id.koZnaZnaCard).setOnClickListener(v ->
                openStepByStep(v)
        );

        view.findViewById(R.id.spojniceCard).setOnClickListener(v ->
                openRegisteredOnly(v, R.id.spojniceFragment)
        );

        view.findViewById(R.id.asocijacijeCard).setOnClickListener(v ->
                openRegisteredOnly(v, R.id.asocijacijeFragment)
        );

        view.findViewById(R.id.skockoCard).setOnClickListener(v ->
                openRegisteredOnly(v, R.id.skockoWaitingRoomFragment)
        );

        view.findViewById(R.id.stepByStepCard).setOnClickListener(v ->
                openStepByStep(v)
        );

        view.findViewById(R.id.myNumberCard).setOnClickListener(v ->
                openRegisteredOnly(v, R.id.myNumberFragment)
        );

        return view;
    }

    private void openStepByStep(View view) {
        AuthUser currentUser = AuthService.getInstance(requireContext()).getCurrentUser();
        if (currentUser != null && currentUser.isGuest()) {
            Bundle args = new Bundle();
            args.putString("roomId", HomeFragment.GUEST_FRIENDLY_ROOM_ID);
            Navigation.findNavController(view).navigate(R.id.stepByStepWaitingRoomFragment, args);
            return;
        }
        Navigation.findNavController(view).navigate(R.id.stepByStepWaitingRoomFragment);
    }

    private void openRegisteredOnly(View view, int destinationId) {
        AuthUser currentUser = AuthService.getInstance(requireContext()).getCurrentUser();
        if (currentUser != null && currentUser.isGuest()) {
            Toast.makeText(requireContext(),
                    "Gost moze da igra samo prijateljsku partiju protiv drugog igraca.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Navigation.findNavController(view).navigate(destinationId);
    }
}
