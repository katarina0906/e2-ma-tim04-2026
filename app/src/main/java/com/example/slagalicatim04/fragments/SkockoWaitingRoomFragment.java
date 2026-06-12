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
import com.example.slagalicatim04.auth.AuthService;
import com.example.slagalicatim04.auth.AuthUser;
import com.example.slagalicatim04.multiplayer.TestRoomPlayerProvider;
import com.example.slagalicatim04.skocko.SkockoMatchRepository;
import com.example.slagalicatim04.skocko.SkockoMatchState;
import com.example.slagalicatim04.stepbystep.StepByStepPlayerSession;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

public class SkockoWaitingRoomFragment extends Fragment {
    private static final String ROOM_ID = SkockoMatchRepository.DEFAULT_MATCH_ID;

    private SkockoMatchRepository repository;
    private StepByStepPlayerSession playerSession;
    private ListenerRegistration listenerRegistration;
    private boolean navigatedToGame;

    private TextView statusText;
    private TextView player1Text;
    private TextView player2Text;
    private MaterialButton confirmButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_step_by_step_waiting_room, container, false);
        TextView titleText = view.findViewById(R.id.waitingRoomTitleText);
        statusText = view.findViewById(R.id.waitingRoomStatusText);
        TextView codeText = view.findViewById(R.id.waitingRoomCodeText);
        player1Text = view.findViewById(R.id.waitingRoomPlayer1Text);
        player2Text = view.findViewById(R.id.waitingRoomPlayer2Text);
        confirmButton = view.findViewById(R.id.waitingRoomConfirmButton);
        MaterialButton resetButton = view.findViewById(R.id.waitingRoomResetButton);

        playerSession = resolveCurrentUser();
        repository = new SkockoMatchRepository(ROOM_ID);
        titleText.setText("Skočko");
        codeText.setText("Skočko test soba: " + ROOM_ID);
        confirmButton.setEnabled(false);
        confirmButton.setOnClickListener(v ->
                repository.confirmReady(playerSession, this::showError));
        resetButton.setOnClickListener(v -> {
            navigatedToGame = false;
            repository.resetRoom(playerSession);
        });

        repository.joinRoom(playerSession, this::showError);
        listenerRegistration = repository.listen(new SkockoMatchRepository.Listener() {
            @Override
            public void onStateChanged(SkockoMatchState state) {
                renderState(state);
            }

            @Override
            public void onError(Exception error) {
                showError(error);
            }
        });
        return view;
    }

    @Override
    public void onDestroyView() {
        if (listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
        }
        super.onDestroyView();
    }

    private void renderState(SkockoMatchState state) {
        int myPlayer = state.playerNumber(playerSession.getId());
        player1Text.setText("Igrac 1: " + playerLabel(
                state.getPlayer1Name(), state.isPlayer1Ready()));
        player2Text.setText("Igrac 2: " + playerLabel(
                state.getPlayer2Name(), state.isPlayer2Ready()));

        if (SkockoMatchState.PHASE_ROUND.equals(state.getPhase())
                || SkockoMatchState.PHASE_STEAL.equals(state.getPhase())) {
            navigateToGame();
            return;
        }
        if (!state.hasSecondPlayer()) {
            statusText.setText("Ceka se drugi igrac.");
        } else if (myPlayer == 0) {
            statusText.setText("Soba je popunjena. Resetuj test sobu za novi test.");
        } else if (state.isReady(myPlayer)) {
            statusText.setText("Spreman si. Ceka se potvrda drugog igraca.");
        } else {
            statusText.setText("Oba igraca su u sobi. Klikni potvrdu kad si spreman.");
        }
        confirmButton.setEnabled(myPlayer != 0 && state.hasSecondPlayer()
                && !state.isReady(myPlayer));
    }

    private void navigateToGame() {
        if (navigatedToGame || !isAdded()) {
            return;
        }
        navigatedToGame = true;
        Bundle args = new Bundle();
        args.putString("roomId", ROOM_ID);
        Navigation.findNavController(requireView()).navigate(R.id.skockoFragment, args);
    }

    private StepByStepPlayerSession resolveCurrentUser() {
        AuthUser authUser = AuthService.getInstance(requireContext()).getCurrentUser();
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        String userId = new TestRoomPlayerProvider(requireContext()).getPlayerId();
        String userName;
        if (authUser != null) {
            userName = authUser.getUsername().isEmpty()
                    ? authUser.getEmail() : authUser.getUsername();
        } else if (firebaseUser != null) {
            userName = firebaseUser.getEmail() == null
                    ? firebaseUser.getUid() : firebaseUser.getEmail();
        } else {
            userName = "Gost";
        }
        return new StepByStepPlayerSession(userId, userName);
    }

    private String playerLabel(String name, boolean ready) {
        if (isEmpty(name)) {
            return "ceka se";
        }
        return name + (ready ? " - spreman" : " - nije potvrdio");
    }

    private void showError(Exception error) {
        if (isAdded()) {
            Toast.makeText(requireContext(), error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
