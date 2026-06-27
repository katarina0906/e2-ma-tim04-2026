package com.example.slagalicatim04.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.slagalicatim04.R;
import com.example.slagalicatim04.auth.PlayerHeaderLoader;
import com.example.slagalicatim04.friends.GameSessionRepository;
import com.example.slagalicatim04.models.MatchingMultiplayerState;
import com.example.slagalicatim04.models.MatchingPair;
import com.example.slagalicatim04.repositories.MatchForfeitRepository;
import com.example.slagalicatim04.repositories.MultiplayerGameRepository;

public class SpojniceFragment extends Fragment implements ExitConfirmationHandler {

    private static final long CHANCE_DURATION_MS = 30_000L;
    private static final int COLOR_DEFAULT = Color.rgb(111, 75, 178);
    private static final int COLOR_SELECTED = Color.rgb(249, 168, 37);
    private static final int COLOR_CORRECT = Color.rgb(76, 175, 80);
    private static final int COLOR_FORFEITED = 0xFFD32F2F;

    private final MatchingPair[][] rounds = {
            {
                    new MatchingPair("Dino Merlin", "Nedostajes"),
                    new MatchingPair("Zdravko Colic", "Ti si mi u krvi"),
                    new MatchingPair("Adele", "Easy On Me"),
                    new MatchingPair("Ceca", "Vreteno"),
                    new MatchingPair("Bajaga", "Moji drugovi")
            },
            {
                    new MatchingPair("Francuska", "Pariz"),
                    new MatchingPair("Italija", "Rim"),
                    new MatchingPair("Spanija", "Madrid"),
                    new MatchingPair("Portugal", "Lisabon"),
                    new MatchingPair("Austrija", "Bec")
            }
    };
    private final int[][] rightOrder = {
            {1, 4, 0, 2, 3},
            {3, 0, 4, 1, 2}
    };
    private final int[] leftIds = {
            R.id.left0, R.id.left1, R.id.left2, R.id.left3, R.id.left4
    };
    private final int[] rightIds = {
            R.id.right0, R.id.right1, R.id.right2, R.id.right3, R.id.right4
    };
    private final Button[] leftButtons = new Button[5];
    private final Button[] rightButtons = new Button[5];

    private TextView roundText;
    private TextView turnText;
    private TextView timerText;
    private TextView resultText;
    private TextView playerOneScoreText;
    private TextView playerTwoScoreText;
    private ImageView playerOneAvatar;
    private ImageView playerTwoAvatar;
    private CountDownTimer timer;
    private MultiplayerGameRepository multiplayerRepository;
    private MultiplayerGameRepository.Subscription stateRegistration;
    private MatchingMultiplayerState currentState;
    private String roomId = MultiplayerGameRepository.TEST_ROOM_ID;
    private MatchForfeitRepository forfeitRepository;
    private int selectedLeftIndex = -1;
    private String timerChanceKey = "";
    private boolean submitting;
    private boolean navigatedToAssociations;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_spojnice, container, false);
        roundText = view.findViewById(R.id.roundText);
        turnText = view.findViewById(R.id.turnText);
        timerText = view.findViewById(R.id.timerText);
        resultText = view.findViewById(R.id.resultText);
        playerOneScoreText = view.findViewById(R.id.spScore0);
        playerTwoScoreText = view.findViewById(R.id.spScore1);
        playerOneAvatar = view.findViewById(R.id.spAvatar0);
        playerTwoAvatar = view.findViewById(R.id.spAvatar1);
        view.findViewById(R.id.newSpojniceGameButton).setVisibility(View.GONE);

        for (int index = 0; index < leftButtons.length; index++) {
            leftButtons[index] = view.findViewById(leftIds[index]);
            rightButtons[index] = view.findViewById(rightIds[index]);
            setupLeftButton(index);
            setupRightButton(index);
        }

        if (getArguments() != null && !isEmpty(getArguments().getString("roomId"))) {
            roomId = getArguments().getString("roomId");
        }
        multiplayerRepository = new MultiplayerGameRepository(requireContext(), roomId);
        forfeitRepository = new MatchForfeitRepository(roomId);
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(), new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (!handleExitRequest()) {
                            setEnabled(false);
                            requireActivity().getOnBackPressedDispatcher().onBackPressed();
                        }
                    }
                });
        showWaitingState();
        stateRegistration = multiplayerRepository.joinMatching(
                new MultiplayerGameRepository.StateListener<MatchingMultiplayerState>() {
                    @Override
                    public void onState(MatchingMultiplayerState state) {
                        if (isAdded()) {
                            renderState(state);
                        }
                    }

                    @Override
                    public void onError(Exception error) {
                        if (isAdded()) {
                            showError(error);
                        }
                    }
                });
        return view;
    }

    private void setupLeftButton(int index) {
        leftButtons[index].setOnClickListener(v -> {
            if (!canPlay() || currentState.isMatched(index) || currentState.isAttempted(index)) {
                return;
            }
            clearSelection();
            selectedLeftIndex = index;
            leftButtons[index].setBackgroundColor(COLOR_SELECTED);
            resultText.setText("Izaberi pojam iz desne kolone za: " + leftButtons[index].getText());
        });
    }

    private void setupRightButton(int displayedRightIndex) {
        rightButtons[displayedRightIndex].setOnClickListener(v -> {
            if (!canPlay() || selectedLeftIndex < 0) {
                resultText.setText(canPlay()
                        ? "Prvo izaberi pojam iz leve kolone."
                        : "Sacekaj svoj potez.");
                return;
            }

            int leftIndex = selectedLeftIndex;
            int pairIndex = rightOrder[currentState.getCurrentRound()][displayedRightIndex];
            selectedLeftIndex = -1;
            submitting = true;
            setButtonsForState(currentState);
            resultText.setText("Slanje pokusaja...");
            multiplayerRepository.submitMatchingAttempt(
                    currentState.getCurrentRound(), leftIndex, pairIndex);
        });
    }

    private void renderState(MatchingMultiplayerState state) {
        currentState = state;
        submitting = false;
        selectedLeftIndex = -1;
        updateScores(state);
        updateHeaderVisibility(state.isSoloChallenge());

        if ("next".equals(state.getStatus())) {
            navigateToAssociations();
            return;
        }
        if ("finished".equals(state.getStatus())) {
            finishGame(state);
            return;
        }
        if (!"playing".equals(state.getStatus())) {
            showWaitingState(state);
            return;
        }

        MatchingPair[] currentRound = rounds[state.getCurrentRound()];
        for (int index = 0; index < leftButtons.length; index++) {
            leftButtons[index].setText(currentRound[index].getLeft());
            int pairIndex = rightOrder[state.getCurrentRound()][index];
            rightButtons[index].setText(currentRound[pairIndex].getRight());
        }

        roundText.setText("Runda " + (state.getCurrentRound() + 1) + " / 2");
        String playerLabel = state.getCurrentPlayer().equals(state.getPlayer1Id())
                ? playerLabel(state.getPlayer1Id(), state.getPlayer1Name(), "Igrac 1")
                : playerLabel(state.getPlayer2Id(), state.getPlayer2Name(), "Igrac 2");
        if (state.hasForfeit()) {
            turnText.setText("Protivnik je napustio partiju");
        } else {
            turnText.setText("Na potezu: " + playerLabel
                    + (state.isSecondChance() ? " (preostali parovi)" : " (pocinje rundu)"));
        }
        if (state.hasForfeit()) {
            resultText.setText(nonEmpty(state.getStatusMessage(),
                    "Protivnik je napustio partiju. Nastavljas bez cekanja."));
        } else if (state.getCurrentPlayer().equals(multiplayerRepository.getPlayerId())) {
            resultText.setText("Tvoj potez.");
        } else {
            resultText.setText("Sacekaj potez drugog igraca.");
        }

        setButtonsForState(state);
        if (state.hasForfeit() && state.isForfeited(state.getCurrentPlayer())) {
            resultText.setText(nonEmpty(state.getStatusMessage(),
                    "Protivnik je napustio partiju. Preskace se cekanje."));
            multiplayerRepository.expireMatchingChance(
                    state.getCurrentRound(), state.getCurrentPlayer(), state.isSecondChance());
            return;
        }
        startTimerForChance(state);
    }

    private void setButtonsForState(MatchingMultiplayerState state) {
        boolean myTurn = canPlay();
        for (int leftIndex = 0; leftIndex < leftButtons.length; leftIndex++) {
            boolean matched = state.isMatched(leftIndex);
            boolean attempted = state.isAttempted(leftIndex);
            leftButtons[leftIndex].setEnabled(myTurn && !matched && !attempted);
            leftButtons[leftIndex].setBackgroundColor(matched ? COLOR_CORRECT : COLOR_DEFAULT);
        }

        for (int displayedRightIndex = 0; displayedRightIndex < rightButtons.length;
             displayedRightIndex++) {
            int pairIndex = rightOrder[state.getCurrentRound()][displayedRightIndex];
            boolean matched = state.isMatched(pairIndex);
            rightButtons[displayedRightIndex].setEnabled(myTurn && !matched);
            rightButtons[displayedRightIndex].setBackgroundColor(matched ? COLOR_CORRECT : COLOR_DEFAULT);
        }
    }

    private boolean canPlay() {
        return currentState != null
                && "playing".equals(currentState.getStatus())
                && currentState.getCurrentPlayer().equals(multiplayerRepository.getPlayerId())
                && !submitting;
    }

    private void clearSelection() {
        if (selectedLeftIndex >= 0 && currentState != null
                && !currentState.isMatched(selectedLeftIndex)) {
            leftButtons[selectedLeftIndex].setBackgroundColor(COLOR_DEFAULT);
        }
        selectedLeftIndex = -1;
    }

    private void startTimerForChance(MatchingMultiplayerState state) {
        String chanceKey = state.getCurrentRound() + ":" + state.getCurrentPlayer()
                + ":" + state.isSecondChance();
        if (chanceKey.equals(timerChanceKey) && timer != null) {
            return;
        }
        cancelTimer();
        timerChanceKey = chanceKey;
        int roundIndex = state.getCurrentRound();
        String expectedPlayer = state.getCurrentPlayer();
        boolean secondChance = state.isSecondChance();

        timer = new CountDownTimer(CHANCE_DURATION_MS, 250L) {
            @Override
            public void onTick(long millisUntilFinished) {
                timerText.setText(Math.max(1L, (millisUntilFinished + 999L) / 1000L) + "s");
            }

            @Override
            public void onFinish() {
                timerText.setText("0s");
                disableAllButtons();
                multiplayerRepository.expireMatchingChance(
                        roundIndex, expectedPlayer, secondChance);
            }
        }.start();
    }

    private void updateScores(MatchingMultiplayerState state) {
        playerOneScoreText.setText(playerLabel(state.getPlayer1Id(), state.getPlayer1Name(), "Igrac 1") + ": "
                + state.getScore(state.getPlayer1Id()));
        playerTwoScoreText.setText(playerLabel(state.getPlayer2Id(), state.getPlayer2Name(), "Igrac 2") + ": "
                + state.getScore(state.getPlayer2Id()));
        playerOneScoreText.setTextColor(state.isForfeited(state.getPlayer1Id()) ? COLOR_FORFEITED : Color.BLACK);
        playerTwoScoreText.setTextColor(state.isForfeited(state.getPlayer2Id()) ? COLOR_FORFEITED : Color.BLACK);
        PlayerHeaderLoader.loadAvatar(state.getPlayer1Id(), playerOneAvatar);
        if (!state.isSoloChallenge()) {
            PlayerHeaderLoader.loadAvatar(state.getPlayer2Id(), playerTwoAvatar);
        }
    }

    private void updateHeaderVisibility(boolean soloChallenge) {
        int visibility = soloChallenge ? View.GONE : View.VISIBLE;
        playerTwoScoreText.setVisibility(visibility);
        playerTwoAvatar.setVisibility(visibility);
    }

    private String playerLabel(String playerId, String name, String fallback) {
        if (name != null && !name.trim().isEmpty()) {
            return name;
        }
        return playerId == null || playerId.trim().isEmpty() ? fallback : playerId;
    }

    private void showWaitingState() {
        showWaitingState(null);
    }

    private void showWaitingState(MatchingMultiplayerState state) {
        cancelTimer();
        timerChanceKey = "";
        roundText.setText("");
        boolean opponentLeft = state != null && state.hasForfeit();
        boolean hasState = state != null;
        turnText.setText(opponentLeft
                ? "Protivnik je napustio partiju"
                : (hasState ? "Stanje partije je sacuvano" : "Ceka se drugi igrac"));
        timerText.setText("30s");
        resultText.setText(opponentLeft
                ? nonEmpty(state.getStatusMessage(), "Stanje partije je sacuvano u bazi.")
                : (hasState
                ? nonEmpty(state.getStatusMessage(), "Partija je aktivna. Sacekaj sledece stanje iz baze.")
                : "Oba uredjaja treba da otvore igru Spojnice."));
        disableAllButtons();
    }

    private void finishGame(MatchingMultiplayerState state) {
        cancelTimer();
        roundText.setText("Spojnice zavrsene");
        turnText.setText("Kraj igre");
        timerText.setText("0s");
        resultText.setText(state.isSoloChallenge()
                ? "Rezultat: " + state.getScore(state.getPlayer1Id())
                : "Igrac 1: " + state.getScore(state.getPlayer1Id())
                + " | Igrac 2: " + state.getScore(state.getPlayer2Id()));
        disableAllButtons();
    }

    private void showError(Exception error) {
        cancelTimer();
        resultText.setText("Firestore greska: " + error.getMessage());
        disableAllButtons();
    }

    private void disableAllButtons() {
        for (Button button : leftButtons) {
            button.setEnabled(false);
        }
        for (Button button : rightButtons) {
            button.setEnabled(false);
        }
    }

    private void cancelTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private void navigateToAssociations() {
        if (navigatedToAssociations || getView() == null) {
            return;
        }
        navigatedToAssociations = true;
        Bundle args = new Bundle();
        args.putString("roomId", roomId);
        Navigation.findNavController(requireView()).navigate(R.id.asocijacijeFragment, args);
    }

    private String nonEmpty(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Override
    public boolean handleExitRequest() {
        if (currentState == null
                || !"playing".equals(currentState.getStatus())) {
            return false;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("Napusti partiju?")
                .setMessage("Ako izađeš sada, izgubićeš partiju. Da li želiš da napustiš igru?")
                .setNegativeButton("Ostani", null)
                .setPositiveButton("Napusti", (dialog, which) -> {
                    forfeitRepository.forfeit(multiplayerRepository.getPlayerId());
                    Navigation.findNavController(requireView()).navigate(
                            R.id.homeFragment,
                            null,
                            new androidx.navigation.NavOptions.Builder()
                                    .setPopUpTo(R.id.nav_graph, true)
                                    .build());
                })
                .show();
        return true;
    }

    @Override
    public void onDestroyView() {
        cancelTimer();
        timerChanceKey = "";
        if (multiplayerRepository != null) {
            multiplayerRepository.leaveMatchingWaitingRoom();
        }
        if (stateRegistration != null) {
            stateRegistration.remove();
            stateRegistration = null;
        }
        if (!navigatedToAssociations) {
            new GameSessionRepository().abandonRoom(roomId);
        }
        super.onDestroyView();
    }
}
