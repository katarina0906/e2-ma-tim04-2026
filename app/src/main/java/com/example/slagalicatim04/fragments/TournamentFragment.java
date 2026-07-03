package com.example.slagalicatim04.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;

import com.example.slagalicatim04.R;
import com.example.slagalicatim04.auth.AuthService;
import com.example.slagalicatim04.auth.AuthUser;
import com.example.slagalicatim04.tournament.TournamentParticipant;
import com.example.slagalicatim04.tournament.TournamentRepository;
import com.example.slagalicatim04.tournament.TournamentState;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.ListenerRegistration;

public class TournamentFragment extends Fragment {
    public static final String ARG_TOURNAMENT_ID = "tournamentId";

    private final TournamentRepository repository = new TournamentRepository();
    private ListenerRegistration registration;
    private AuthUser currentUser;
    private String tournamentId = "";
    private TextView status;
    private TextView[] playerViews;
    private TextView bracket;
    private MaterialButton joinButton;
    private MaterialButton playButton;
    private MaterialButton homeButton;
    private String currentRoomId = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tournament, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        currentUser = AuthService.getInstance(requireContext()).getCurrentUser();
        status = view.findViewById(R.id.tournamentStatus);
        bracket = view.findViewById(R.id.tournamentBracket);
        joinButton = view.findViewById(R.id.tournamentJoinButton);
        playButton = view.findViewById(R.id.tournamentPlayButton);
        homeButton = view.findViewById(R.id.tournamentHomeButton);
        playerViews = new TextView[]{
                view.findViewById(R.id.tournamentPlayer1),
                view.findViewById(R.id.tournamentPlayer2),
                view.findViewById(R.id.tournamentPlayer3),
                view.findViewById(R.id.tournamentPlayer4)
        };
        joinButton.setOnClickListener(v -> joinTournament());
        playButton.setOnClickListener(v -> openCurrentMatch());
        homeButton.setOnClickListener(v -> navigateHome());
        playButton.setVisibility(View.GONE);
        homeButton.setVisibility(View.GONE);
        if (getArguments() != null) {
            String existingTournamentId = getArguments().getString(ARG_TOURNAMENT_ID, "");
            if (!existingTournamentId.isEmpty()) {
                tournamentId = existingTournamentId;
                listenTournament(existingTournamentId);
            }
        }
    }

    private void joinTournament() {
        if (currentUser == null) {
            Toast.makeText(requireContext(), "Korisnik nije prijavljen.", Toast.LENGTH_LONG).show();
            return;
        }
        joinButton.setEnabled(false);
        status.setText("Trazi se turnir...");
        repository.join(currentUser, new TournamentRepository.JoinCallback() {
            @Override
            public void onJoined(String id) {
                tournamentId = id;
                listenTournament(id);
            }

            @Override
            public void onError(Exception error) {
                joinButton.setEnabled(true);
                status.setText("Turnir nije pokrenut.");
                Toast.makeText(requireContext(), error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void listenTournament(String id) {
        if (registration != null) {
            registration.remove();
        }
        registration = repository.listen(id, new TournamentRepository.Listener() {
            @Override
            public void onState(TournamentState state) {
                render(state);
            }

            @Override
            public void onError(Exception error) {
                Toast.makeText(requireContext(), error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void render(TournamentState state) {
        if (!isAdded() || currentUser == null) {
            return;
        }
        joinButton.setVisibility(View.GONE);
        for (int i = 0; i < playerViews.length; i++) {
            if (state.participants.size() > i) {
                TournamentParticipant p = state.participants.get(i);
                playerViews[i].setText(p.username + "\n" + p.league);
            } else {
                playerViews[i].setText("Ceka se igrac\n-");
            }
        }
        currentRoomId = state.roomFor(currentUser.getId());
        playButton.setVisibility(currentRoomId.isEmpty() || "finished".equals(state.status)
                ? View.GONE : View.VISIBLE);
        homeButton.setVisibility("finished".equals(state.status) ? View.VISIBLE : View.GONE);
        playButton.setText("final".equals(state.status) ? "Igraj finale" : "Otvori moju partiju");
        if ("waiting".equals(state.status)) {
            status.setText("Ceka se 4 aktivna igraca (" + state.participants.size() + "/4).");
            bracket.setText("Polufinala se formiraju cim se popune sva 4 mesta.");
        } else if ("semifinals".equals(state.status)) {
            status.setText("Polufinale je spremno. Igraju se dve partije istovremeno.");
            bracket.setText(semifinalsText(state));
            animateStatus();
        } else if ("final".equals(state.status)) {
            status.setText("Finale je u toku.");
            bracket.setText(finalInProgressText(state));
            animateStatus();
        } else if ("finished".equals(state.status)) {
            status.setText("Turnir je zavrsen.");
            bracket.setText(finishedText(state));
            animateStatus();
        }
    }

    private String names(TournamentState state, int first, int second) {
        if (state.participants.size() <= second) return "-";
        return state.participants.get(first).username + " protiv "
                + state.participants.get(second).username;
    }

    private String semifinalsText(TournamentState state) {
        StringBuilder builder = new StringBuilder();
        builder.append("Polufinale 1: ").append(names(state, 0, 1));
        if (!state.semifinal1WinnerId.isEmpty()) {
            builder.append("\nPobednik: ")
                    .append(nameFor(state, state.semifinal1WinnerId))
                    .append(" (")
                    .append(state.semifinal1WinnerScore)
                    .append(" bodova)");
        }
        builder.append("\n\nPolufinale 2: ").append(names(state, 2, 3));
        if (!state.semifinal2WinnerId.isEmpty()) {
            builder.append("\nPobednik: ")
                    .append(nameFor(state, state.semifinal2WinnerId))
                    .append(" (")
                    .append(state.semifinal2WinnerScore)
                    .append(" bodova)");
        }
        if (state.semifinal1WinnerId.isEmpty() || state.semifinal2WinnerId.isEmpty()) {
            builder.append("\n\nCeka se zavrsetak oba polufinala.");
        } else {
            builder.append("\n\nPobednici idu u finale.");
        }
        return builder.toString();
    }

    private String finalInProgressText(TournamentState state) {
        StringBuilder builder = new StringBuilder();
        builder.append("1-2. mesto: finale u toku\n")
                .append(nameFor(state, state.semifinal1WinnerId))
                .append(" protiv ")
                .append(nameFor(state, state.semifinal2WinnerId));
        if (state.hasBothSemifinalLosers()) {
            builder.append("\n\n").append(semifinalLosersPlacement(state));
        }
        return builder.toString();
    }

    private String finishedText(TournamentState state) {
        StringBuilder builder = new StringBuilder();
        builder.append("1. ")
                .append(nameFor(state, state.championId))
                .append(" - ")
                .append(state.championScore)
                .append(" bodova");
        builder.append("\n2. ")
                .append(nameFor(state, state.runnerUpId))
                .append(" - ")
                .append(state.runnerUpScore)
                .append(" bodova");
        if (state.hasBothSemifinalLosers()) {
            builder.append("\n").append(semifinalLosersPlacement(state));
        }
        return builder.toString();
    }

    private String semifinalLosersPlacement(TournamentState state) {
        String firstLoser = state.semifinal1LoserId;
        String secondLoser = state.semifinal2LoserId;
        long firstScore = state.semifinal1LoserScore;
        long secondScore = state.semifinal2LoserScore;
        boolean firstIsThird = firstScore >= secondScore;
        String thirdId = firstIsThird ? firstLoser : secondLoser;
        long thirdScore = firstIsThird ? firstScore : secondScore;
        String fourthId = firstIsThird ? secondLoser : firstLoser;
        long fourthScore = firstIsThird ? secondScore : firstScore;
        return "3. " + nameFor(state, thirdId) + " - " + thirdScore + " bodova"
                + "\n4. " + nameFor(state, fourthId) + " - " + fourthScore + " bodova";
    }

    private String nameFor(TournamentState state, String userId) {
        for (TournamentParticipant p : state.participants) {
            if (p.userId.equals(userId)) return p.username;
        }
        return "-";
    }

    private void openCurrentMatch() {
        if (currentRoomId.isEmpty()) return;
        Bundle args = new Bundle();
        args.putString("roomId", currentRoomId);
        Navigation.findNavController(requireView()).navigate(R.id.stepByStepWaitingRoomFragment, args);
    }

    private void navigateHome() {
        Navigation.findNavController(requireView()).navigate(
                R.id.homeFragment,
                null,
                new NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .setPopUpTo(R.id.homeFragment, false)
                        .build());
    }

    private void animateStatus() {
        AlphaAnimation animation = new AlphaAnimation(0.35f, 1f);
        animation.setDuration(500);
        status.startAnimation(animation);
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
