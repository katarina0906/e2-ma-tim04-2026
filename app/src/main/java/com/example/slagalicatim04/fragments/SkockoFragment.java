package com.example.slagalicatim04.fragments;

import android.content.Context;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.slagalicatim04.R;

import java.util.Arrays;
import java.util.Random;

/**
 * Skočko: 2 runde. U svakoj rundi samo igrač koji je započeo rundu ima do 6 pokušaja u 30 s;
 * ako ne pogodi, protivnik dobija jedan ukradeni pokušaj (10 s, 10 bodova) na njegovu kombinaciju.
 * Znakovi: skocko, herc, krug, zvezda, kvadrat, trougao.
 */
public class SkockoFragment extends Fragment {

    private static final int CODE_LEN = 4;
    private static final int NUM_SYMBOLS = 6;
    private static final int MAX_ATTEMPTS = 6;
    private static final long TURN_MS = 30_000L;
    private static final long STEAL_MS = 10_000L;

    private static final int[] SYMBOL_DRAWABLES = {
            R.drawable.skocko_znak_skocko,
            R.drawable.skocko_znak_herc,
            R.drawable.skocko_znak_krug,
            R.drawable.skocko_znak_zvezda,
            R.drawable.skocko_znak_kvadrat,
            R.drawable.skocko_znak_trougao,
    };

    private static final int[] PALETTE_IDS = {
            R.id.skPal0, R.id.skPal1, R.id.skPal2, R.id.skPal3, R.id.skPal4, R.id.skPal5,
    };

    private TextView roundText;
    private TextView statusText;
    private TextView timerText;
    private TextView stealTimerText;
    private TextView score0;
    private TextView score1;
    private TextView resultText;
    private LinearLayout skHistoryBlock;
    private View stealCard;
    private ImageView[] draftSlots = new ImageView[CODE_LEN];
    private Button clearButton;
    private Button submitButton;
    private Button nextRoundButton;
    private Button newGameButton;

    private final ImageView[][] guessSlots = new ImageView[MAX_ATTEMPTS][CODE_LEN];
    private final View[][] pegViews = new View[MAX_ATTEMPTS][CODE_LEN];

    private final int[][] secrets = new int[2][CODE_LEN];
    private final int[] totalScores = new int[2];

    private final int[] draft = new int[]{-1, -1, -1, -1};
    private final int[][] committedGuess = new int[MAX_ATTEMPTS][CODE_LEN];
    private final int[][] feedbackBw = new int[MAX_ATTEMPTS][2];
    private int numCommitted;

    private int roundIndex;
    private int roundStarter;
    /** Ko trenutno pogađa svoju kombinaciju (uvek igrač koji je započeo rundu). */
    private int solvingPlayer;
    /** Čija tajna kombinacija se pogađa u ukradenom pokušaju (onaj koji nije pogodio svoju). */
    private int stealVictim;
    private boolean stealPhase;
    private boolean stealAttemptDone;
    private boolean gameOver;

    private CountDownTimer phaseTimer;

    public SkockoFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_skocko, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        roundText = view.findViewById(R.id.skRoundText);
        statusText = view.findViewById(R.id.skStatusText);
        timerText = view.findViewById(R.id.skTimerText);
        stealTimerText = view.findViewById(R.id.skStealTimer);
        score0 = view.findViewById(R.id.skScore0);
        score1 = view.findViewById(R.id.skScore1);
        resultText = view.findViewById(R.id.skResultText);
        skHistoryBlock = view.findViewById(R.id.skHistoryBlock);
        stealCard = view.findViewById(R.id.skStealCard);
        draftSlots[0] = view.findViewById(R.id.skDraft0);
        draftSlots[1] = view.findViewById(R.id.skDraft1);
        draftSlots[2] = view.findViewById(R.id.skDraft2);
        draftSlots[3] = view.findViewById(R.id.skDraft3);
        clearButton = view.findViewById(R.id.skClearButton);
        submitButton = view.findViewById(R.id.skSubmitButton);
        nextRoundButton = view.findViewById(R.id.skNextRoundButton);
        newGameButton = view.findViewById(R.id.skNewGameButton);

        buildHistoryRows();
        for (int i = 0; i < NUM_SYMBOLS; i++) {
            final int sym = i;
            view.findViewById(PALETTE_IDS[i]).setOnClickListener(v -> onPaletteTap(sym));
        }
        clearButton.setOnClickListener(v -> clearDraft());
        submitButton.setOnClickListener(v -> onSubmit());
        nextRoundButton.setOnClickListener(v -> {
            roundIndex++;
            nextRoundButton.setVisibility(View.GONE);
            startRound();
        });
        newGameButton.setOnClickListener(v -> startNewGame());

        startNewGame();
    }

    private void buildHistoryRows() {
        Context ctx = requireContext();
        float d = ctx.getResources().getDisplayMetrics().density;
        int m = Math.round(4 * d);
        int slotH = Math.round(40 * d);
        int pegS = Math.round(12 * d);

        for (int r = 0; r < MAX_ATTEMPTS; r++) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = m;
            row.setLayoutParams(rowLp);

            TextView num = new TextView(ctx);
            num.setText(String.valueOf(r + 1));
            num.setWidth(Math.round(26 * d));
            num.setGravity(Gravity.CENTER);
            num.setTextColor(0xFF888888);
            row.addView(num);

            for (int c = 0; c < CODE_LEN; c++) {
                ImageView iv = new ImageView(ctx);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, slotH, 1f);
                lp.setMarginEnd(m / 2);
                iv.setLayoutParams(lp);
                iv.setBackgroundResource(R.drawable.skocko_slot_bg);
                int pad = Math.round(6 * d);
                iv.setPadding(pad, pad, pad, pad);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                row.addView(iv);
                guessSlots[r][c] = iv;
            }

            GridLayout pegGrid = new GridLayout(ctx);
            pegGrid.setColumnCount(2);
            pegGrid.setRowCount(2);
            for (int i = 0; i < CODE_LEN; i++) {
                View peg = new View(ctx);
                GridLayout.LayoutParams plp = new GridLayout.LayoutParams();
                plp.width = pegS;
                plp.height = pegS;
                plp.setMargins(2, 2, 2, 2);
                peg.setLayoutParams(plp);
                peg.setBackgroundResource(R.drawable.skocko_peg_empty);
                pegGrid.addView(peg);
                pegViews[r][i] = peg;
            }
            row.addView(pegGrid);
            skHistoryBlock.addView(row);
        }
    }

    private void startNewGame() {
        cancelTimer();
        totalScores[0] = 0;
        totalScores[1] = 0;
        roundIndex = 0;
        gameOver = false;
        newGameButton.setVisibility(View.GONE);
        nextRoundButton.setVisibility(View.GONE);
        updateScoreUi();
        startRound();
    }

    private void startRound() {
        cancelTimer();
        stealPhase = false;
        stealAttemptDone = false;
        stealCard.setVisibility(View.GONE);
        nextRoundButton.setVisibility(View.GONE);
        newGameButton.setVisibility(View.GONE);
        roundStarter = roundIndex % 2;
        generateSecrets();
        clearHistoryUi();
        numCommitted = 0;
        clearDraft();
        solvingPlayer = roundStarter;
        roundText.setText(getString(R.string.sk_round_fmt, roundIndex + 1, 2));
        resultText.setText("");
        updateStatus();
        enableInputs(true);
        startPhaseTimer(TURN_MS);
    }

    private void generateSecrets() {
        Random rnd = new Random();
        for (int p = 0; p < 2; p++) {
            for (int i = 0; i < CODE_LEN; i++) {
                secrets[p][i] = rnd.nextInt(NUM_SYMBOLS);
            }
        }
    }

    private void clearHistoryUi() {
        for (int r = 0; r < MAX_ATTEMPTS; r++) {
            Arrays.fill(committedGuess[r], -1);
            feedbackBw[r][0] = feedbackBw[r][1] = -1;
            for (int c = 0; c < CODE_LEN; c++) {
                guessSlots[r][c].setImageDrawable(null);
            }
            setPegs(r, 0, 0, false);
        }
    }

    private void setPegs(int row, int black, int white, boolean active) {
        View[] pegs = pegViews[row];
        for (int i = 0; i < CODE_LEN; i++) {
            if (!active) {
                pegs[i].setBackgroundResource(R.drawable.skocko_peg_empty);
            } else if (i < black) {
                pegs[i].setBackgroundResource(R.drawable.skocko_peg_exact);
            } else if (i < black + white) {
                pegs[i].setBackgroundResource(R.drawable.skocko_peg_partial);
            } else {
                pegs[i].setBackgroundResource(R.drawable.skocko_peg_empty);
            }
        }
    }

    private void onPaletteTap(int symbolIndex) {
        if (gameOver || !canAcceptInput()) return;
        for (int i = 0; i < CODE_LEN; i++) {
            if (draft[i] < 0) {
                draft[i] = symbolIndex;
                updateDraftUi();
                return;
            }
        }
        draft[CODE_LEN - 1] = symbolIndex;
        updateDraftUi();
    }

    private void clearDraft() {
        Arrays.fill(draft, -1);
        updateDraftUi();
    }

    private void updateDraftUi() {
        for (int i = 0; i < CODE_LEN; i++) {
            if (draft[i] < 0) {
                draftSlots[i].setImageDrawable(null);
            } else {
                draftSlots[i].setImageResource(SYMBOL_DRAWABLES[draft[i]]);
            }
        }
    }

    private boolean canAcceptInput() {
        return !gameOver && phaseTimer != null;
    }

    private void enableInputs(boolean on) {
        clearButton.setEnabled(on);
        submitButton.setEnabled(on);
        for (int id : PALETTE_IDS) {
            requireView().findViewById(id).setEnabled(on);
        }
    }

    private void onSubmit() {
        if (gameOver || !canAcceptInput()) return;
        if (!draftComplete()) {
            Toast.makeText(requireContext(), R.string.sk_draft_incomplete, Toast.LENGTH_SHORT).show();
            return;
        }
        if (stealPhase) {
            if (stealAttemptDone) return;
            stealAttemptDone = true;
            int[] g = draftToArray();
            cancelTimer();
            int stealer = 1 - roundStarter;
            if (Arrays.equals(g, secrets[stealVictim])) {
                totalScores[stealer] += 10;
                updateScoreUi();
                Toast.makeText(requireContext(), R.string.sk_steal_ok, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), R.string.sk_steal_fail, Toast.LENGTH_SHORT).show();
            }
            finishRound();
            return;
        }

        int[] guess = draftToArray();
        int secretIdx = solvingPlayer;
        int[] fb = mastermindFeedback(secrets[secretIdx], guess);
        System.arraycopy(guess, 0, committedGuess[numCommitted], 0, CODE_LEN);
        feedbackBw[numCommitted][0] = fb[0];
        feedbackBw[numCommitted][1] = fb[1];
        for (int c = 0; c < CODE_LEN; c++) {
            guessSlots[numCommitted][c].setImageResource(SYMBOL_DRAWABLES[guess[c]]);
        }
        setPegs(numCommitted, fb[0], fb[1], true);
        numCommitted++;
        clearDraft();

        if (fb[0] == CODE_LEN) {
            cancelTimer();
            awardSolve(solvingPlayer, numCommitted);
            updateScoreUi();
            finishRound();
            return;
        }
        if (numCommitted >= MAX_ATTEMPTS) {
            cancelTimer();
            beginSteal();
        }
    }

    private void beginSteal() {
        stealPhase = true;
        stealVictim = roundStarter;
        stealAttemptDone = false;
        stealCard.setVisibility(View.VISIBLE);
        clearDraft();
        numCommitted = 0;
        clearHistoryUi();
        updateStatus();
        timerText.setText("");
        stealTimerText.setText(getString(R.string.sk_timer_fmt, 0, 10));
        enableInputs(true);
        startStealTimer();
    }

    private void finishRound() {
        cancelTimer();
        stealPhase = false;
        stealCard.setVisibility(View.GONE);
        enableInputs(false);
        if (roundIndex >= 1) {
            gameOver = true;
            resultText.setText(getString(R.string.sk_game_over_fmt, totalScores[0], totalScores[1]));
            newGameButton.setVisibility(View.VISIBLE);
        } else {
            resultText.setText(R.string.sk_round_done);
            nextRoundButton.setVisibility(View.VISIBLE);
        }
        phaseTimer = null;
    }

    private void onPhaseTimeUp() {
        if (stealPhase) {
            if (!stealAttemptDone) {
                stealAttemptDone = true;
                finishRound();
            }
            return;
        }
        if (numCommitted > 0 && feedbackBw[numCommitted - 1][0] == CODE_LEN) {
            return;
        }
        Toast.makeText(requireContext(), R.string.sk_time_turn, Toast.LENGTH_SHORT).show();
        beginSteal();
    }

    private void awardSolve(int player, int attemptCount) {
        int pts;
        if (attemptCount <= 2) pts = 20;
        else if (attemptCount <= 4) pts = 15;
        else pts = 10;
        totalScores[player] += pts;
    }

    private static int[] mastermindFeedback(int[] secret, int[] guess) {
        int black = 0;
        boolean[] secU = new boolean[CODE_LEN];
        boolean[] gueU = new boolean[CODE_LEN];
        for (int i = 0; i < CODE_LEN; i++) {
            if (secret[i] == guess[i]) {
                black++;
                secU[i] = true;
                gueU[i] = true;
            }
        }
        int white = 0;
        for (int i = 0; i < CODE_LEN; i++) {
            if (gueU[i]) continue;
            for (int j = 0; j < CODE_LEN; j++) {
                if (secU[j]) continue;
                if (guess[i] == secret[j]) {
                    white++;
                    secU[j] = true;
                    break;
                }
            }
        }
        return new int[]{black, white};
    }

    private boolean draftComplete() {
        for (int v : draft) {
            if (v < 0) return false;
        }
        return true;
    }

    private int[] draftToArray() {
        return new int[]{draft[0], draft[1], draft[2], draft[3]};
    }

    private void startPhaseTimer(long ms) {
        cancelTimer();
        phaseTimer = new CountDownTimer(ms, 250) {
            @Override
            public void onTick(long left) {
                long sec = (left + 999) / 1000;
                long m = sec / 60;
                long s = sec % 60;
                timerText.setText(getString(R.string.sk_timer_fmt, m, s));
            }

            @Override
            public void onFinish() {
                timerText.setText(getString(R.string.sk_timer_fmt, 0, 0));
                onPhaseTimeUp();
            }
        };
        phaseTimer.start();
    }

    private void startStealTimer() {
        cancelTimer();
        phaseTimer = new CountDownTimer(STEAL_MS, 250) {
            @Override
            public void onTick(long left) {
                long sec = (left + 999) / 1000;
                stealTimerText.setText(getString(R.string.sk_timer_fmt, 0, sec));
            }

            @Override
            public void onFinish() {
                stealTimerText.setText(getString(R.string.sk_timer_fmt, 0, 0));
                onPhaseTimeUp();
            }
        };
        phaseTimer.start();
    }

    private void cancelTimer() {
        if (phaseTimer != null) {
            phaseTimer.cancel();
            phaseTimer = null;
        }
    }

    private void updateStatus() {
        if (gameOver) {
            statusText.setText("");
            return;
        }
        if (stealPhase) {
            int stealer = 1 - roundStarter;
            statusText.setText(getString(R.string.sk_status_steal, stealer + 1, stealVictim + 1));
            return;
        }
        statusText.setText(getString(R.string.sk_status_solve, solvingPlayer + 1, roundStarter + 1));
    }

    private void updateScoreUi() {
        score0.setText(getString(R.string.sk_player_pts, 1, totalScores[0]));
        score1.setText(getString(R.string.sk_player_pts, 2, totalScores[1]));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelTimer();
    }
}
