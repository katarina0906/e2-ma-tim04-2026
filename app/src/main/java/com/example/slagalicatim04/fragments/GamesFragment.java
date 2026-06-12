package com.example.slagalicatim04.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.slagalicatim04.R;

public class GamesFragment extends Fragment {

    public GamesFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_games, container, false);

        view.findViewById(R.id.koZnaZnaCard).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.stepByStepWaitingRoomFragment)
        );

        view.findViewById(R.id.spojniceCard).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.spojniceFragment)
        );

        view.findViewById(R.id.asocijacijeCard).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.asocijacijeFragment)
        );

        view.findViewById(R.id.skockoCard).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.skockoFragment)
        );

        view.findViewById(R.id.stepByStepCard).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.stepByStepWaitingRoomFragment)
        );

        view.findViewById(R.id.myNumberCard).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.myNumberFragment)
        );

        return view;
    }
}
