package com.example.slagalicatim04.fragments;

import android.content.Context;
import android.os.Bundle;
import android.os.CountDownTimer;
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
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.slagalicatim04.R;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Asocijacije: tabla AB / konačno / CD; unos preko dijaloga sa automatskom tastaturom.
 */
public class AsocijacijeFragment extends Fragment {

    private static final long ROUND_MS = 120_000L;
    private static final int COLS = 4;
    private static final int ROWS = 4;

    private static final int[][] CELL_IDS = {
            {R.id.aso_b00, R.id.aso_b01, R.id.aso_b02, R.id.aso_b03},
            {R.id.aso_b10, R.id.aso_b11, R.id.aso_b12, R.id.aso_b13},
            {R.id.aso_b20, R.id.aso_b21, R.id.aso_b22, R.id.aso_b23},
            {R.id.aso_b30, R.id.aso_b31, R.id.aso_b32, R.id.aso_b33},
    };

    private static final int[] TITLE_IDS = {
            R.id.aso_title0, R.id.aso_title1, R.id.aso_title2, R.id.aso_title3,
    };

    private static final Puzzle[] PUZZLES = {
            new Puzzle(
                    new String[][]{
                            {"Jabuka", "Kruška", "Banana", "Narandža"},
                            {"Krompir", "Luk", "Šargarepa", "Kupus"},
                            {"Pšenica", "Ječam", "Raž", "Ovas"},
                            {"Sir", "Jogurt", "Kajmak", "Pavlaka"},
                    },
                    new String[]{"VOĆE", "POVRĆE", "ŽITO", "MLEČNO"},
                    "HRANA"
            ),
            new Puzzle(
                    new String[][]{
                            {"Crvena", "Plava", "Zelena", "Žuta"},
                            {"Trougao", "Kvadrat", "Krug", "Elipsa"},
                            {"Violina", "Gitara", "Klavir", "Bubanj"},
                            {"Drama", "Komedija", "Tragedija", "Mjuzikl"},
                    },
                    new String[]{"BOJE", "OBLICI", "INSTRUMENTI", "ŽANROVI"},
                    "UMETNOST"
            ),
    };

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

    private final Button[][] cellButtons = new Button[COLS][ROWS];
    private final TextView[] titleViews = new TextView[COLS];

    private CountDownTimer timer;

    private Puzzle puzzle;
    private final boolean[][] revealed = new boolean[COLS][ROWS];
    private final boolean[] columnSolved = new boolean[COLS];
    private boolean finalSolved;

    private boolean openPhase = true;
    private int currentPlayer;
    private int roundIndex;
    private boolean roundFrozen;
    private boolean bothRoundsDone;

    private final int[] totalScores = new int[2];
    private final int[] roundScores = new int[2];

    public AsocijacijeFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_asocijacije, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

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

        for (int c = 0; c < COLS; c++) {
            titleViews[c] = view.findViewById(TITLE_IDS[c]);
            final int col = c;
            titleViews[c].setOnClickListener(v -> onColumnTitleTapped(col));
            for (int r = 0; r < ROWS; r++) {
                cellButtons[c][r] = view.findViewById(CELL_IDS[c][r]);
                final int row = r;
                cellButtons[c][r].setOnClickListener(v -> onCellClicked(col, row));
            }
        }

        finalTitle.setOnClickListener(v -> onFinalTitleTapped());
        passGuessButton.setOnClickListener(v -> onPassGuess());
        nextRoundButton.setOnClickListener(v -> onNextRound());
        newGameButton.setOnClickListener(v -> onNewGame());

        startFreshGame();
    }

    /**
     * Otvara dijalog sa poljem za unos; tastatura se traži čim se dijalog prikaže.
     */
    private void showGuessDialog(boolean isFinal, int col) {
        Context ctx = requireContext();
        final EditText input = new EditText(ctx);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setHint(R.string.aso_guess_hint);
        int pad = (int) (20 * ctx.getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad / 2, pad, pad / 2);
        input.setMinHeight((int) (48 * ctx.getResources().getDisplayMetrics().density));

        String title = isFinal
                ? getString(R.string.aso_dialog_title_final)
                : getString(R.string.aso_dialog_title_column, String.valueOf((char) ('A' + col)));

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(title)
                .setView(input)
                .setPositiveButton(R.string.aso_confirm_guess, null)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .create();

        dialog.setOnShowListener(d -> {
            Window win = dialog.getWindow();
            if (win != null) {
                win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
            input.requestFocus();
            InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            input.post(() -> imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT));

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String guess = input.getText().toString();
                if (TextUtils.isEmpty(guess)) {
                    Toast.makeText(ctx, R.string.aso_empty_guess, Toast.LENGTH_SHORT).show();
                    return;
                }
                dialog.dismiss();
                if (isFinal) {
                    submitFinalGuess(guess);
                } else {
                    submitColumnGuess(col, guess);
                }
            });
        });

        dialog.show();
    }

    private void updateTitleClickableState() {
        boolean alive = !roundFrozen && !bothRoundsDone;
        for (int c = 0; c < COLS; c++) {
            boolean colClick = alive && !openPhase && !columnSolved[c];
            titleViews[c].setClickable(colClick);
            titleViews[c].setFocusable(colClick);
        }
        boolean finClick = alive && !openPhase && !finalSolved;
        finalTitle.setClickable(finClick);
        finalTitle.setFocusable(finClick);
    }

    private void onColumnTitleTapped(int col) {
        if (roundFrozen || bothRoundsDone) return;
        if (openPhase) {
            Toast.makeText(requireContext(), R.string.aso_open_first, Toast.LENGTH_SHORT).show();
            return;
        }
        if (columnSolved[col]) {
            Toast.makeText(requireContext(), R.string.aso_column_done, Toast.LENGTH_SHORT).show();
            return;
        }
        showGuessDialog(false, col);
    }

    private void onFinalTitleTapped() {
        if (roundFrozen || bothRoundsDone) return;
        if (openPhase) {
            Toast.makeText(requireContext(), R.string.aso_open_first, Toast.LENGTH_SHORT).show();
            return;
        }
        if (finalSolved) return;
        showGuessDialog(true, 0);
    }

    private void submitColumnGuess(int col, String guess) {
        if (columnSolved[col]) {
            Toast.makeText(requireContext(), R.string.aso_column_done, Toast.LENGTH_SHORT).show();
            return;
        }
        if (matches(guess, puzzle.columnAnswers[col])) {
            int pts = columnPoints(col);
            addPoints(pts);
            columnSolved[col] = true;
            revealWholeColumn(col);
            Toast.makeText(requireContext(), getString(R.string.aso_column_ok, pts), Toast.LENGTH_SHORT).show();
            refreshBoardUi();
            if (allColumnsSolved()) {
                resultText.setText(R.string.aso_all_columns_hint);
            }
        } else {
            Toast.makeText(requireContext(), R.string.aso_wrong_switch, Toast.LENGTH_SHORT).show();
            switchTurnAfterMistake();
        }
    }

    private void submitFinalGuess(String guess) {
        if (finalSolved) return;
        if (matches(guess, puzzle.finalAnswer)) {
            int pts = finalPoints();
            addPoints(pts);
            finalSolved = true;
            finalTitle.setText(puzzle.finalAnswer);
            Toast.makeText(requireContext(), getString(R.string.aso_final_ok, pts), Toast.LENGTH_LONG).show();
            endRoundSuccess();
        } else {
            Toast.makeText(requireContext(), R.string.aso_wrong_switch, Toast.LENGTH_SHORT).show();
            switchTurnAfterMistake();
        }
    }

    private void startFreshGame() {
        bothRoundsDone = false;
        totalScores[0] = 0;
        totalScores[1] = 0;
        roundIndex = 0;
        nextRoundButton.setVisibility(View.GONE);
        newGameButton.setVisibility(View.GONE);
        startRound();
    }

    private void startRound() {
        cancelTimer();
        roundFrozen = false;
        finalSolved = false;
        openPhase = true;
        currentPlayer = roundIndex % 2;
        roundScores[0] = 0;
        roundScores[1] = 0;
        puzzle = PUZZLES[roundIndex % PUZZLES.length];

        for (int c = 0; c < COLS; c++) {
            columnSolved[c] = false;
            for (int r = 0; r < ROWS; r++) {
                revealed[c][r] = false;
            }
        }

        roundText.setText(getString(R.string.aso_round_label, roundIndex + 1, 2));
        updateScoreUi();
        refreshBoardUi();
        updateTurnUi();
        resultText.setText("");
        nextRoundButton.setVisibility(View.GONE);
        newGameButton.setVisibility(View.GONE);
        enableGuessControls(true);
        startTimer();
    }

    private void startTimer() {
        cancelTimer();
        timer = new CountDownTimer(ROUND_MS, 250) {
            @Override
            public void onTick(long millisUntilFinished) {
                long sec = (millisUntilFinished + 999) / 1000;
                long m = sec / 60;
                long s = sec % 60;
                timerText.setText(getString(R.string.aso_timer_fmt, m, s));
            }

            @Override
            public void onFinish() {
                timerText.setText(getString(R.string.aso_timer_fmt, 0, 0));
                onRoundTimeUp();
            }
        }.start();
    }

    private void cancelTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private void onRoundTimeUp() {
        if (roundFrozen) return;
        roundFrozen = true;
        cancelTimer();
        enableGuessControls(false);
        applyCellLockState();
        resultText.setText(R.string.aso_time_up);
        Toast.makeText(requireContext(), R.string.aso_time_up, Toast.LENGTH_LONG).show();
        finishRoundTransition();
    }

    private void onCellClicked(int col, int row) {
        if (roundFrozen || bothRoundsDone) return;
        if (!openPhase) {
            Toast.makeText(requireContext(), R.string.aso_must_guess_or_pass, Toast.LENGTH_SHORT).show();
            return;
        }
        if (revealed[col][row] || columnSolved[col]) return;

        revealed[col][row] = true;
        openPhase = false;
        refreshBoardUi();
    }

    private void onPassGuess() {
        if (roundFrozen || bothRoundsDone) return;
        if (openPhase) {
            Toast.makeText(requireContext(), R.string.aso_open_first_pass, Toast.LENGTH_SHORT).show();
            return;
        }
        switchTurnAfterMistake();
    }

    private void switchTurnAfterMistake() {
        currentPlayer = 1 - currentPlayer;
        openPhase = true;
        updateTurnUi();
        refreshBoardUi();
    }

    private void endRoundSuccess() {
        roundFrozen = true;
        cancelTimer();
        enableGuessControls(false);
        applyCellLockState();
        resultText.setText(R.string.aso_round_solved_final);
        finishRoundTransition();
    }

    private void finishRoundTransition() {
        enableGuessControls(false);
        applyCellLockState();
        if (roundIndex >= 1) {
            bothRoundsDone = true;
            nextRoundButton.setVisibility(View.GONE);
            newGameButton.setVisibility(View.VISIBLE);
            resultText.setText(getString(R.string.aso_game_over,
                    totalScores[0], totalScores[1]));
        } else {
            nextRoundButton.setVisibility(View.VISIBLE);
            newGameButton.setVisibility(View.GONE);
        }
    }

    private void onNextRound() {
        roundIndex++;
        startRound();
    }

    private void onNewGame() {
        startFreshGame();
    }

    private void enableGuessControls(boolean enabled) {
        boolean g = enabled && !roundFrozen && !bothRoundsDone;
        passGuessButton.setEnabled(g);
        updateTitleClickableState();
    }

    private void applyCellLockState() {
        if (roundFrozen || bothRoundsDone) {
            for (int c = 0; c < COLS; c++) {
                for (int r = 0; r < ROWS; r++) {
                    cellButtons[c][r].setEnabled(false);
                }
            }
            return;
        }
        for (int c = 0; c < COLS; c++) {
            for (int r = 0; r < ROWS; r++) {
                boolean done = revealed[c][r] || columnSolved[c];
                cellButtons[c][r].setEnabled(!done && openPhase);
            }
        }
    }

    private void refreshBoardUi() {
        for (int c = 0; c < COLS; c++) {
            if (columnSolved[c]) {
                titleViews[c].setText(puzzle.columnAnswers[c]);
            } else {
                titleViews[c].setText("•••••");
            }
            titleViews[c].setBackgroundResource(R.drawable.aso_title_field_bg);
            for (int r = 0; r < ROWS; r++) {
                if (columnSolved[c] || revealed[c][r]) {
                    cellButtons[c][r].setText(puzzle.clues[c][r]);
                    cellButtons[c][r].setEnabled(false);
                } else {
                    cellButtons[c][r].setText("?");
                    cellButtons[c][r].setEnabled(!roundFrozen && !bothRoundsDone);
                }
            }
        }
        if (finalSolved) {
            finalTitle.setText(puzzle.finalAnswer);
        } else {
            finalTitle.setText("• • • • • • •");
        }
        finalTitle.setBackgroundResource(R.drawable.aso_final_field_bg);
        updatePhaseHint();
        applyCellLockState();
        updateTitleClickableState();
    }

    private void updateTurnUi() {
        turnText.setText(getString(R.string.aso_turn_fmt, currentPlayer + 1));
    }

    private void updatePhaseHint() {
        if (roundFrozen || bothRoundsDone) {
            phaseHint.setText("");
            return;
        }
        if (openPhase) {
            phaseHint.setText(getString(R.string.aso_phase_open, currentPlayer + 1));
        } else {
            phaseHint.setText(getString(R.string.aso_phase_guess, currentPlayer + 1));
        }
    }

    private void updateScoreUi() {
        scoreP1.setText(getString(R.string.aso_player_points, 1, totalScores[0]));
        scoreP2.setText(getString(R.string.aso_player_points, 2, totalScores[1]));
        roundPointsText.setText(getString(R.string.aso_round_points_fmt,
                roundScores[0], roundScores[1]));
    }

    private void addPoints(int pts) {
        totalScores[currentPlayer] += pts;
        roundScores[currentPlayer] += pts;
        updateScoreUi();
    }

    private int hiddenClueCount(int col) {
        int n = 0;
        for (int r = 0; r < ROWS; r++) {
            if (!revealed[col][r]) n++;
        }
        return n;
    }

    private int openedClueCount(int col) {
        return ROWS - hiddenClueCount(col);
    }

    private int columnPoints(int col) {
        return 2 + hiddenClueCount(col);
    }

    private int finalPoints() {
        int pts = 7;
        for (int c = 0; c < COLS; c++) {
            if (columnSolved[c]) continue;
            if (openedClueCount(c) == 0) {
                pts += 6;
            } else {
                pts += 2 + hiddenClueCount(c);
            }
        }
        return pts;
    }

    private void revealWholeColumn(int col) {
        for (int r = 0; r < ROWS; r++) {
            revealed[col][r] = true;
        }
    }

    private boolean allColumnsSolved() {
        for (boolean s : columnSolved) {
            if (!s) return false;
        }
        return true;
    }

    private static boolean matches(String guess, String answer) {
        return norm(guess).equals(norm(answer));
    }

    private static String norm(String s) {
        if (s == null) return "";
        String t = Normalizer.normalize(s.trim(), Normalizer.Form.NFD);
        t = t.replaceAll("\\p{M}+", "");
        return t.toLowerCase(Locale.ROOT);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelTimer();
    }

    private static final class Puzzle {
        final String[][] clues;
        final String[] columnAnswers;
        final String finalAnswer;

        Puzzle(String[][] clues, String[] columnAnswers, String finalAnswer) {
            this.clues = clues;
            this.columnAnswers = columnAnswers;
            this.finalAnswer = finalAnswer;
        }
    }
}
