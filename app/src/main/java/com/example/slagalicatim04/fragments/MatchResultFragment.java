package com.example.slagalicatim04.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.slagalicatim04.R;
import com.example.slagalicatim04.auth.PlayerHeaderLoader;
import com.example.slagalicatim04.matchresult.MatchResultRepository;
import com.example.slagalicatim04.matchresult.MatchResultState;
import com.example.slagalicatim04.repositories.MultiplayerGameRepository;
import com.google.firebase.firestore.ListenerRegistration;

public class MatchResultFragment extends Fragment {
    private String roomId = MultiplayerGameRepository.TEST_ROOM_ID;
    private ListenerRegistration registration;
    private TextView winnerText;
    private TextView player1Name;
    private TextView player1Score;
    private TextView player2Name;
    private TextView player2Score;
    private ImageView player1Avatar;
    private ImageView player2Avatar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_match_result, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getArguments() != null && !isEmpty(getArguments().getString("roomId"))) {
            roomId = getArguments().getString("roomId");
        }
        winnerText = view.findViewById(R.id.matchResultWinner);
        player1Name = view.findViewById(R.id.matchResultPlayer1Name);
        player1Score = view.findViewById(R.id.matchResultPlayer1Score);
        player2Name = view.findViewById(R.id.matchResultPlayer2Name);
        player2Score = view.findViewById(R.id.matchResultPlayer2Score);
        player1Avatar = view.findViewById(R.id.matchResultPlayer1Avatar);
        player2Avatar = view.findViewById(R.id.matchResultPlayer2Avatar);
        view.findViewById(R.id.matchResultHomeButton).setOnClickListener(
                button -> Navigation.findNavController(button).navigate(R.id.homeFragment));

        registration = new MatchResultRepository().listen(roomId,
                new MatchResultRepository.Listener() {
                    @Override
                    public void onState(MatchResultState state) {
                        render(state);
                    }

                    @Override
                    public void onError(Exception error) {
                        if (isAdded()) {
                            Toast.makeText(requireContext(), error.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void render(MatchResultState state) {
        if (!state.isMatchResult() || !isAdded()) {
            return;
        }
        String firstName = playerName(state.getPlayer1Name(), "Igrac 1");
        String secondName = playerName(state.getPlayer2Name(), "Igrac 2");
        player1Name.setText(firstName);
        player2Name.setText(secondName);
        player1Score.setText(scoreSummary(state.getPlayer1Score(), state.getPlayer1StarDelta(),
                state.getPlayer1EarnedTokens()));
        player2Score.setText(scoreSummary(state.getPlayer2Score(), state.getPlayer2StarDelta(),
                state.getPlayer2EarnedTokens()));
        PlayerHeaderLoader.loadAvatar(state.getPlayer1Id(), player1Avatar);
        PlayerHeaderLoader.loadAvatar(state.getPlayer2Id(), player2Avatar);
        if (state.winner() == 1) {
            winnerText.setText("Pobednik je " + firstName + "!");
        } else if (state.winner() == 2) {
            winnerText.setText("Pobednik je " + secondName + "!");
        } else {
            winnerText.setText("Partija je zavrsena nereseno.");
        }
    }

    private String playerName(String name, String fallback) {
        return isEmpty(name) ? fallback : name;
    }

    private String scoreSummary(long score, long starDelta, long earnedTokens) {
        StringBuilder builder = new StringBuilder();
        builder.append(score).append(" bodova");
        if (starDelta != 0) {
            builder.append(" • ");
            if (starDelta > 0) {
                builder.append("+");
            }
            builder.append(starDelta).append(" zvezda");
        }
        if (earnedTokens > 0) {
            builder.append(" • +").append(earnedTokens).append(" token");
        }
        return builder.toString();
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Override
    public void onDestroyView() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        super.onDestroyView();
    }
}
