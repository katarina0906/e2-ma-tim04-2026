package com.example.slagalicatim04.fragments;

import android.os.Bundle;
import android.provider.Settings;
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
import com.example.slagalicatim04.friends.GameSessionRepository;
import com.example.slagalicatim04.stepbystep.StepByStepMatchRepository;
import com.example.slagalicatim04.stepbystep.StepByStepMatchState;
import com.example.slagalicatim04.stepbystep.StepByStepPlayerSession;
import com.example.slagalicatim04.stepbystep.StepByStepWaitingRoomRepository;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

public class StepByStepWaitingRoomFragment extends Fragment {
    private String roomId = StepByStepMatchRepository.DEFAULT_MATCH_ID;

    private StepByStepWaitingRoomRepository repository;
    private StepByStepPlayerSession playerSession;
    private ListenerRegistration listenerRegistration;
    private boolean navigatedToGame;

    private TextView statusText;
    private TextView codeText;
    private TextView player1Text;
    private TextView player2Text;
    private MaterialButton confirmButton;
    private MaterialButton resetButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_step_by_step_waiting_room, container, false);

        statusText = view.findViewById(R.id.waitingRoomStatusText);
        codeText = view.findViewById(R.id.waitingRoomCodeText);
        player1Text = view.findViewById(R.id.waitingRoomPlayer1Text);
        player2Text = view.findViewById(R.id.waitingRoomPlayer2Text);
        confirmButton = view.findViewById(R.id.waitingRoomConfirmButton);
        resetButton = view.findViewById(R.id.waitingRoomResetButton);

        if (getArguments() != null && !isEmpty(getArguments().getString("roomId"))) {
            roomId = getArguments().getString("roomId");
        }
        playerSession = resolveCurrentUser();
        if (!hasAvailableTokens()) {
            Toast.makeText(requireContext(), R.string.tokens_missing, Toast.LENGTH_LONG).show();
            Navigation.findNavController(view).navigate(
                    R.id.homeFragment,
                    null,
                    new androidx.navigation.NavOptions.Builder()
                            .setPopUpTo(R.id.nav_graph, true)
                            .build());
            return view;
        }
        repository = new StepByStepWaitingRoomRepository(roomId);
        codeText.setText("");
        confirmButton.setEnabled(false);
        confirmButton.setOnClickListener(v -> repository.confirmReady(playerSession, this::showError));
        resetButton.setOnClickListener(v -> {
            navigatedToGame = false;
            repository.resetRoom(playerSession);
        });

        repository.joinRoom(playerSession, this::showError);
        listenToRoom();
        return view;
    }

    @Override
    public void onDestroyView() {
        if (listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
        }
        if (!navigatedToGame) {
            new GameSessionRepository().abandonRoom(roomId);
        }
        super.onDestroyView();
    }

    private void listenToRoom() {
        listenerRegistration = repository.listen(new StepByStepWaitingRoomRepository.RoomListener() {
            @Override
            public void onStateChanged(StepByStepMatchState state) {
                renderState(state);
            }

            @Override
            public void onError(Exception error) {
                showError(error);
            }
        });
    }

    private void renderState(StepByStepMatchState state) {
        int myPlayer = state.playerNumber(playerSession.getId());
        boolean opponentLeft = state.hasForfeit();
        player1Text.setText("Igrac 1: " + playerLabel(state.getPlayer1Id(), state.getPlayer1Name(),
                state.isPlayer1Ready(), state));
        player2Text.setText("Igrac 2: " + playerLabel(state.getPlayer2Id(), state.getPlayer2Name(),
                state.isPlayer2Ready(), state));
        player1Text.setTextColor(state.isForfeited(state.getPlayer1Id()) ? 0xFFD32F2F : 0xFF000000);
        player2Text.setTextColor(state.isForfeited(state.getPlayer2Id()) ? 0xFFD32F2F : 0xFF000000);

        if ("koZnaZnaPlaying".equals(state.getPhase())) {
            navigateToKoZnaZna();
            return;
        }

        if (opponentLeft) {
            statusText.setText(nonEmpty(state.getStatusMessage(),
                    "Protivnik je napustio partiju."));
        } else if (!state.hasSecondPlayer()) {
            statusText.setText("Ceka se drugi igrac.");
        } else if (myPlayer == 0) {
            statusText.setText("Soba je popunjena. Resetuj test sobu za novi test.");
        } else if (state.isReady(myPlayer)) {
            statusText.setText("Spreman si. Ceka se potvrda drugog igraca.");
        } else {
            statusText.setText("Oba igraca su u sobi. Klikni potvrdu kad si spreman.");
        }

        confirmButton.setEnabled(myPlayer != 0 && state.hasSecondPlayer() && !state.isReady(myPlayer));
    }

    private String playerLabel(String playerId, String name, boolean ready, StepByStepMatchState state) {
        String visibleName = !isEmpty(name) ? name : (!isEmpty(playerId) ? playerId : "");
        if (isEmpty(visibleName)) {
            return "ceka se";
        }
        if (state.isForfeited(playerId)) {
            return visibleName + " - napustio partiju";
        }
        return visibleName + (ready ? " - spreman" : " - nije potvrdio");
    }

    private void navigateToKoZnaZna() {
        if (navigatedToGame || !isAdded()) {
            return;
        }
        navigatedToGame = true;
        Bundle args = new Bundle();
        args.putString("roomId", roomId);
        Navigation.findNavController(requireView()).navigate(R.id.koZnaZnaFragment, args);
    }

    private StepByStepPlayerSession resolveCurrentUser() {
        AuthUser authUser = AuthService.getInstance(requireContext()).getCurrentUser();
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        String userId;
        String userName;

        if (authUser != null) {
            userId = authUser.getId();
            userName = authUser.getUsername().isEmpty() ? authUser.getEmail() : authUser.getUsername();
        } else if (firebaseUser != null) {
            userId = firebaseUser.getUid();
            userName = firebaseUser.getEmail() == null ? firebaseUser.getUid() : firebaseUser.getEmail();
        } else {
            userId = "guest";
            userName = "Gost";
        }
        return new StepByStepPlayerSession(userId, userName);
    }

    private boolean hasAvailableTokens() {
        AuthUser authUser = AuthService.getInstance(requireContext()).getCurrentUser();
        return authUser == null || authUser.getTokens() > 0;
    }

    private String deviceId() {
        String id = Settings.Secure.getString(
                requireContext().getContentResolver(),
                Settings.Secure.ANDROID_ID
        );
        return isEmpty(id) ? String.valueOf(System.currentTimeMillis()) : id;
    }

    private void showError(Exception error) {
        if (isAdded()) {
            Toast.makeText(requireContext(), error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String nonEmpty(String value, String fallback) {
        return isEmpty(value) ? fallback : value;
    }
}
