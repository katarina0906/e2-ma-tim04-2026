package com.example.slagalicatim04.fragments;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.slagalicatim04.R;
import com.example.slagalicatim04.associations.AssociationMatchRepository;
import com.example.slagalicatim04.associations.AssociationMatchState;
import com.example.slagalicatim04.associations.AssociationPuzzle;
import com.example.slagalicatim04.associations.AssociationPuzzleRepository;
import com.example.slagalicatim04.auth.AuthService;
import com.example.slagalicatim04.auth.AuthUser;
import com.example.slagalicatim04.friends.GameSessionRepository;
import com.example.slagalicatim04.multiplayer.TestRoomPlayerProvider;
import com.example.slagalicatim04.repositories.MatchForfeitRepository;
import com.example.slagalicatim04.repositories.MultiplayerGameRepository;
import com.example.slagalicatim04.stepbystep.StepByStepPlayerSession;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AsocijacijeFragment extends Fragment implements ExitConfirmationHandler {
    private static final int COLUMN_COUNT = 4;
    private static final int ROW_COUNT = 4;

    private static final int[][] CELL_IDS = {
            {R.id.aso_b00, R.id.aso_b01, R.id.aso_b02, R.id.aso_b03},
            {R.id.aso_b10, R.id.aso_b11, R.id.aso_b12, R.id.aso_b13},
            {R.id.aso_b20, R.id.aso_b21, R.id.aso_b22, R.id.aso_b23},
            {R.id.aso_b30, R.id.aso_b31, R.id.aso_b32, R.id.aso_b33},
    };
    private static final int[] TITLE_IDS = {
            R.id.aso_title0, R.id.aso_title1, R.id.aso_title2, R.id.aso_title3,
    };

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = this::renderState;
    private final Button[][] cellButtons = new Button[COLUMN_COUNT][ROW_COUNT];
    private final TextView[] titleViews = new TextView[COLUMN_COUNT];
    private final Map<String, AssociationPuzzle> puzzles = new HashMap<>();

    private TextView roundText;
    private TextView turnText;
    private TextView timerText;
    private TextView scoreP1;
    private TextView scoreP2;
    private TextView roundPointsText;
    private TextView phaseHint;
    private TextView finalTitle;
    private TextView resultText;
    private Button passGuessButton;
    private Button nextRoundButton;
    private Button newGameButton;

    private AssociationMatchRepository matchRepository;
    private MatchForfeitRepository forfeitRepository;
    private ListenerRegistration listenerRegistration;
    private AssociationMatchState currentState;
    private AssociationPuzzle currentPuzzle;
    private StepByStepPlayerSession playerSession;
    private String roomId = MultiplayerGameRepository.TEST_ROOM_ID;
    private long lastClockTickAt;
    private boolean navigatedToSkocko;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_asocijacije, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        if (getArguments() != null && !isEmpty(getArguments().getString("roomId"))) {
            roomId = getArguments().getString("roomId");
        }
        playerSession = resolveCurrentUser();
        matchRepository = new AssociationMatchRepository(roomId);
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

        for (int column = 0; column < COLUMN_COUNT; column++) {
            titleViews[column] = view.findViewById(TITLE_IDS[column]);
            int selectedColumn = column;
            titleViews[column].setOnClickListener(
                    ignored -> showGuessDialog(false, selectedColumn));
            for (int row = 0; row < ROW_COUNT; row++) {
                cellButtons[column][row] = view.findViewById(CELL_IDS[column][row]);
                int selectedRow = row;
                cellButtons[column][row].setOnClickListener(
                        ignored -> openCell(selectedColumn, selectedRow));
            }
        }
        finalTitle.setOnClickListener(ignored -> showGuessDialog(true, 0));
        passGuessButton.setOnClickListener(ignored -> matchRepository.pass(playerSession));
        nextRoundButton.setVisibility(View.GONE);
        newGameButton.setVisibility(View.GONE);
        setBoardEnabled(false);
        resultText.setText("Ucitavanje asocijacija iz baze...");
        loadPuzzles();
    }

    private void bindViews(View view) {
        roundText = view.findViewById(R.id.asoRoundText);
        turnText = view.findViewById(R.id.asoTurnText);
        timerText = view.findViewById(R.id.asoTimerText);
        scoreP1 = view.findViewById(R.id.asoScoreP1);
        scoreP2 = view.findViewById(R.id.asoScoreP2);
        roundPointsText = view.findViewById(R.id.asoRoundPointsText);
        phaseHint = view.findViewById(R.id.asoPhaseHint);
        finalTitle = view.findViewById(R.id.asoFinalTitle);
        resultText = view.findViewById(R.id.asoResultText);
        passGuessButton = view.findViewById(R.id.asoPassGuessButton);
        nextRoundButton = view.findViewById(R.id.asoNextRoundButton);
        newGameButton = view.findViewById(R.id.asoNewGameButton);
    }

    private void loadPuzzles() {
        new AssociationPuzzleRepository().loadOrSeed(
                new AssociationPuzzleRepository.Callback() {
                    @Override
                    public void onSuccess(List<AssociationPuzzle> loaded) {
                        puzzles.clear();
                        for (AssociationPuzzle puzzle : loaded) {
                            puzzles.put(puzzle.getId(), puzzle);
                        }
                        listenToMatch();
                    }

                    @Override
                    public void onError(Exception error) {
                        showError(error);
                    }
                });
    }

    private void listenToMatch() {
        listenerRegistration = matchRepository.listen(new AssociationMatchRepository.Listener() {
            @Override
            public void onState(AssociationMatchState state) {
                currentState = state;
                currentPuzzle = puzzles.get(state.getPuzzleId());
                renderState();
            }

            @Override
            public void onError(Exception error) {
                showError(error);
            }
        });
    }

    private void renderState() {
        uiHandler.removeCallbacks(ticker);
        if (!isAdded() || currentState == null) {
            return;
        }
        if ("skocko".equals(currentState.getCurrentGame())) {
            navigateToSkocko();
            return;
        }
        if (!currentState.isAssociationGame()) {
            setBoardEnabled(false);
            resultText.setText("Ceka se pocetak Asocijacija.");
            return;
        }
        if (currentPuzzle == null) {
            setBoardEnabled(false);
            resultText.setText("Tabla za ovu rundu nije pronadjena u bazi.");
            return;
        }

        int myPlayer = currentState.playerNumber(playerSession.getId());
        if (currentState.isForfeited(currentState.getPlayer1Id())
                || currentState.isForfeited(currentState.getPlayer2Id())) {
            matchRepository.resolveForfeitTurn(currentState);
        }
        boolean myTurn = myPlayer != 0 && myPlayer == currentState.getActivePlayer();
        roundText.setText(getString(R.string.aso_round_label, currentState.getRound(),
                currentState.isSoloChallenge() ? 1 : 2));
        if (!currentState.isSoloChallenge() && (currentState.isForfeited(currentState.getPlayer1Id())
                || currentState.isForfeited(currentState.getPlayer2Id()))) {
            turnText.setText("Protivnik je napustio partiju");
        } else {
            String activeLabel = currentState.getActivePlayer() == 1
                    ? playerLabel(currentState.getPlayer1Id(), currentState.getPlayer1Name(), "Igrac 1")
                    : playerLabel(currentState.getPlayer2Id(), currentState.getPlayer2Name(), "Igrac 2");
            turnText.setText("Na potezu: " + activeLabel);
        }
        int seconds = currentState.getSecondsLeft();
        timerText.setText(getString(R.string.aso_timer_fmt, seconds / 60, seconds % 60));
        scoreP1.setText(playerLabel(currentState.getPlayer1Id(), currentState.getPlayer1Name(), "Igrac 1")
                + ": " + (int) currentState.getPlayer1Score());
        scoreP2.setText(playerLabel(currentState.getPlayer2Id(), currentState.getPlayer2Name(), "Igrac 2")
                + ": " + (int) currentState.getPlayer2Score());
        scoreP2.setVisibility(currentState.isSoloChallenge() ? View.GONE : View.VISIBLE);
        scoreP1.setTextColor(currentState.isForfeited(currentState.getPlayer1Id()) ? 0xFFD32F2F : Color.BLACK);
        scoreP2.setTextColor(currentState.isForfeited(currentState.getPlayer2Id()) ? 0xFFD32F2F : Color.BLACK);
        roundPointsText.setText(getString(R.string.aso_round_points_fmt,
                currentState.getRoundPlayer1Score(), currentState.getRoundPlayer2Score()));
        resultText.setText(currentState.getStatusMessage());
        if (!currentState.isSoloChallenge() && (currentState.isForfeited(currentState.getPlayer1Id())
                || currentState.isForfeited(currentState.getPlayer2Id()))) {
            phaseHint.setText(isEmpty(currentState.getStatusMessage())
                    ? "Protivnik je napustio partiju. Nastavljas bez cekanja."
                    : currentState.getStatusMessage());
        } else {
            phaseHint.setText(myTurn
                ? (currentState.isOpenPhase()
                ? "Tvoj potez: otvori jedno skriveno polje."
                : (currentState.canContinueAfterCorrect()
                ? "Tacan odgovor: pogadjaj dalje ili otvori novo polje."
                : "Pogodi kolonu ili konacno resenje, ili predaj potez."))
                : (currentState.isSoloChallenge()
                ? "Samostalna partija je u toku."
                : "Igrac " + currentState.getActivePlayer() + " je na potezu. Cekaj svoj red."));
        }

        renderBoard(myTurn);
        publishClockIfOwner(myPlayer);
        uiHandler.postDelayed(ticker, 1000);
    }

    private void renderBoard(boolean myTurn) {
        for (int column = 0; column < COLUMN_COUNT; column++) {
            boolean solved = currentState.isColumnSolved(column);
            titleViews[column].setText(solved
                    ? currentPuzzle.getColumnAnswer(column) : ".....");
            titleViews[column].setClickable(myTurn && !currentState.isOpenPhase() && !solved);
            titleViews[column].setFocusable(titleViews[column].isClickable());
            for (int row = 0; row < ROW_COUNT; row++) {
                boolean revealed = solved || currentState.isRevealed(column, row);
                cellButtons[column][row].setText(
                        revealed ? currentPuzzle.getClue(column, row) : "?");
                cellButtons[column][row].setEnabled(
                        myTurn
                                && (currentState.isOpenPhase()
                                || currentState.canContinueAfterCorrect())
                                && !revealed && !solved);
            }
        }
        finalTitle.setText(currentState.isFinalSolved()
                ? currentPuzzle.getFinalAnswer() : ". . . . . . .");
        finalTitle.setClickable(myTurn
                && !currentState.isOpenPhase() && !currentState.isFinalSolved());
        finalTitle.setFocusable(finalTitle.isClickable());
        passGuessButton.setEnabled(myTurn && !currentState.isOpenPhase());
    }

    private void publishClockIfOwner(int myPlayer) {
        if (myPlayer != currentState.getActivePlayer()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastClockTickAt < 900) {
            return;
        }
        lastClockTickAt = now;
        matchRepository.tick(playerSession);
    }

    private void openCell(int column, int row) {
        if (canAct() && (currentState.isOpenPhase()
                || currentState.canContinueAfterCorrect())) {
            matchRepository.openCell(playerSession, column, row);
        }
    }

    private void showGuessDialog(boolean finalGuess, int column) {
        if (!canAct() || currentState.isOpenPhase()) {
            return;
        }
        Context context = requireContext();
        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setHint(R.string.aso_guess_hint);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding / 2, padding, padding / 2);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(finalGuess
                        ? getString(R.string.aso_dialog_title_final)
                        : getString(R.string.aso_dialog_title_column,
                        String.valueOf((char) ('A' + column))))
                .setView(input)
                .setPositiveButton(R.string.aso_confirm_guess, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
            input.requestFocus();
            InputMethodManager keyboard = (InputMethodManager)
                    context.getSystemService(Context.INPUT_METHOD_SERVICE);
            input.post(() -> keyboard.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String guess = input.getText().toString();
                if (TextUtils.isEmpty(guess.trim())) {
                    Toast.makeText(context, R.string.aso_empty_guess, Toast.LENGTH_SHORT).show();
                    return;
                }
                dialog.dismiss();
                if (finalGuess) {
                    matchRepository.submitFinalGuess(playerSession, currentPuzzle, guess);
                } else {
                    matchRepository.submitColumnGuess(
                            playerSession, currentPuzzle, column, guess);
                }
            });
        });
        dialog.show();
    }

    private boolean canAct() {
        return currentState != null
                && currentState.isAssociationGame()
                && currentState.playerNumber(playerSession.getId())
                == currentState.getActivePlayer();
    }

    private void setBoardEnabled(boolean enabled) {
        passGuessButton.setEnabled(enabled);
        finalTitle.setClickable(enabled);
        for (int column = 0; column < COLUMN_COUNT; column++) {
            if (titleViews[column] != null) {
                titleViews[column].setClickable(enabled);
            }
            for (int row = 0; row < ROW_COUNT; row++) {
                if (cellButtons[column][row] != null) {
                    cellButtons[column][row].setEnabled(enabled);
                }
            }
        }
    }

    private StepByStepPlayerSession resolveCurrentUser() {
        AuthUser authUser = AuthService.getInstance(requireContext()).getCurrentUser();
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        String id = firebaseUser == null
                ? new TestRoomPlayerProvider(requireContext()).getPlayerId()
                : firebaseUser.getUid();
        String name;
        if (authUser != null) {
            name = authUser.getUsername().isEmpty()
                    ? authUser.getEmail() : authUser.getUsername();
        } else if (firebaseUser != null) {
            name = firebaseUser.getEmail() == null
                    ? firebaseUser.getUid() : firebaseUser.getEmail();
        } else {
            name = "Gost";
        }
        return new StepByStepPlayerSession(id, name);
    }

    private void navigateToSkocko() {
        if (navigatedToSkocko || getView() == null) {
            return;
        }
        navigatedToSkocko = true;
        Bundle args = new Bundle();
        args.putString("roomId", roomId);
        Navigation.findNavController(requireView()).navigate(R.id.skockoFragment, args);
    }

    private void showError(Exception error) {
        if (isAdded()) {
            setBoardEnabled(false);
            resultText.setText("Firestore greska: " + error.getMessage());
            Toast.makeText(requireContext(), error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String playerLabel(String playerId, String name, String fallback) {
        if (!isEmpty(name)) {
            return name;
        }
        return isEmpty(playerId) ? fallback : playerId;
    }

    @Override
    public boolean handleExitRequest() {
        if (currentState == null || "skocko".equals(currentState.getCurrentGame())) {
            return false;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("Napusti partiju?")
                .setMessage("Ako izađeš sada, izgubićeš partiju. Da li želiš da napustiš igru?")
                .setNegativeButton("Ostani", null)
                .setPositiveButton("Napusti", (dialog, which) -> {
                    forfeitRepository.forfeit(playerSession.getId());
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
        uiHandler.removeCallbacks(ticker);
        if (listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
        }
        if (!navigatedToSkocko) {
            new GameSessionRepository().abandonRoom(roomId);
        }
        super.onDestroyView();
    }
}
