package com.example.slagalicatim04.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.slagalicatim04.R;
import com.example.slagalicatim04.models.MatchingPair;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SpojniceFragment extends Fragment {

    private static final long TURN_DURATION_MS = 30_000L;
    private static final int POINTS_PER_PAIR = 2;
    private static final int COLOR_DEFAULT = Color.rgb(111, 75, 178);
    private static final int COLOR_SELECTED = Color.rgb(249, 168, 37);
    private static final int COLOR_CORRECT = Color.rgb(76, 175, 80);
    private static final int COLOR_WRONG = Color.rgb(229, 57, 53);

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

    private final int[] leftIds = {
            R.id.left0, R.id.left1, R.id.left2, R.id.left3, R.id.left4
    };
    private final int[] rightIds = {
            R.id.right0, R.id.right1, R.id.right2, R.id.right3, R.id.right4
    };
    private final int[][] rightOrder = {
            {1, 4, 0, 2, 3},
            {3, 0, 4, 1, 2}
    };

    private final Button[] leftButtons = new Button[5];
    private final Button[] rightButtons = new Button[5];
    private final int[] scores = new int[2];
    private final boolean[] solved = new boolean[5];
    private final Set<Integer> attemptedLeft = new HashSet<>();

    private TextView roundText;
    private TextView turnText;
    private TextView timerText;
    private TextView resultText;
    private TextView playerOneScoreText;
    private TextView playerTwoScoreText;
    private Button newGameButton;
    private CountDownTimer timer;

    private int roundIndex;
    private int activePlayer;
    private int selectedLeftIndex = -1;
    private int chanceVersion;
    private boolean secondChance;
    private boolean gameFinished;
    private boolean inputLocked;

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
        newGameButton = view.findViewById(R.id.newSpojniceGameButton);

        for (int i = 0; i < leftButtons.length; i++) {
            leftButtons[i] = view.findViewById(leftIds[i]);
            rightButtons[i] = view.findViewById(rightIds[i]);
            setupLeftButton(leftButtons[i], i);
            setupRightButton(rightButtons[i], i);
        }

        newGameButton.setOnClickListener(v -> startNewGame());
        startNewGame();
        return view;
    }

    private void setupLeftButton(Button button, int index) {
        button.setOnClickListener(v -> {
            if (gameFinished || inputLocked || solved[index] || attemptedLeft.contains(index)) {
                return;
            }

            clearSelectedLeft();
            selectedLeftIndex = index;
            button.setBackgroundColor(COLOR_SELECTED);
            resultText.setText("Izaberi pojam iz desne kolone za: " + button.getText());
        });
    }

    private void setupRightButton(Button button, int displayedRightIndex) {
        button.setOnClickListener(v -> {
            if (inputLocked) {
                return;
            }
            if (selectedLeftIndex < 0) {
                resultText.setText("Prvo izaberi pojam iz leve kolone.");
                return;
            }

            int leftIndex = selectedLeftIndex;
            int pairIndexOnRight = rightOrder[roundIndex][displayedRightIndex];
            attemptedLeft.add(leftIndex);
            selectedLeftIndex = -1;
            inputLocked = true;
            int handledChanceVersion = chanceVersion;

            if (leftIndex == pairIndexOnRight) {
                handleCorrectPair(leftIndex, displayedRightIndex);
            } else {
                handleWrongPair(leftIndex, displayedRightIndex);
            }

            leftButtons[leftIndex].postDelayed(() -> {
                if (handledChanceVersion == chanceVersion) {
                    inputLocked = false;
                    continueOrFinishTurn();
                }
            }, 700);
        });
    }

    private void handleCorrectPair(int leftIndex, int rightIndex) {
        solved[leftIndex] = true;
        leftButtons[leftIndex].setEnabled(false);
        rightButtons[rightIndex].setEnabled(false);
        leftButtons[leftIndex].setBackgroundColor(COLOR_CORRECT);
        rightButtons[rightIndex].setBackgroundColor(COLOR_CORRECT);

        scores[activePlayer] += POINTS_PER_PAIR;
        updateScores();
        resultText.setText("Tacno! Igrac " + (activePlayer + 1) + " osvaja 2 boda.");
    }

    private void handleWrongPair(int leftIndex, int rightIndex) {
        leftButtons[leftIndex].setEnabled(false);
        leftButtons[leftIndex].setBackgroundColor(COLOR_WRONG);
        rightButtons[rightIndex].setBackgroundColor(COLOR_WRONG);
        resultText.setText("Netacno. Ovaj levi pojam ostaje za drugog igraca.");

        leftButtons[leftIndex].postDelayed(() -> {
            if (!solved[leftIndex]) {
                leftButtons[leftIndex].setBackgroundColor(COLOR_DEFAULT);
            }
            if (rightButtons[rightIndex].isEnabled()) {
                rightButtons[rightIndex].setBackgroundColor(COLOR_DEFAULT);
            }
        }, 650);
    }

    private void continueOrFinishTurn() {
        if (!isAdded() || gameFinished) {
            return;
        }
        if (allPairsSolved()) {
            finishRound();
        } else if (allRemainingPairsAttempted()) {
            finishCurrentChance();
        }
    }

    private void startNewGame() {
        cancelTimer();
        Arrays.fill(scores, 0);
        roundIndex = 0;
        gameFinished = false;
        chanceVersion++;
        newGameButton.setVisibility(View.GONE);
        updateScores();
        startRound();
    }

    private void startRound() {
        Arrays.fill(solved, false);
        attemptedLeft.clear();
        selectedLeftIndex = -1;
        secondChance = false;
        activePlayer = roundIndex;
        inputLocked = false;
        chanceVersion++;

        MatchingPair[] currentRound = rounds[roundIndex];
        for (int i = 0; i < leftButtons.length; i++) {
            leftButtons[i].setText(currentRound[i].getLeft());
            leftButtons[i].setEnabled(true);
            leftButtons[i].setBackgroundColor(COLOR_DEFAULT);

            int pairIndex = rightOrder[roundIndex][i];
            rightButtons[i].setText(currentRound[pairIndex].getRight());
            rightButtons[i].setEnabled(true);
            rightButtons[i].setBackgroundColor(COLOR_DEFAULT);
        }

        roundText.setText("Runda " + (roundIndex + 1) + " / 2");
        resultText.setText("Igrac " + (activePlayer + 1) + " zapocinje rundu.");
        updateTurnText();
        startTimer();
    }

    private void finishCurrentChance() {
        cancelTimer();
        clearSelectedLeft();
        chanceVersion++;
        inputLocked = false;

        if (!secondChance && !allPairsSolved()) {
            secondChance = true;
            activePlayer = 1 - activePlayer;
            attemptedLeft.clear();
            enableUnsolvedPairs();
            resultText.setText("Igrac " + (activePlayer + 1)
                    + " dobija 30 sekundi za preostale parove.");
            updateTurnText();
            startTimer();
        } else {
            finishRound();
        }
    }

    private void finishRound() {
        cancelTimer();
        chanceVersion++;
        inputLocked = true;
        disableAllButtons();

        if (roundIndex == rounds.length - 1) {
            gameFinished = true;
            roundText.setText("Spojnice zavrsene");
            turnText.setText("Kraj igre");
            timerText.setText("0s");
            resultText.setText("Konacan rezultat - Igrac 1: " + scores[0]
                    + ", Igrac 2: " + scores[1]);
            newGameButton.setVisibility(View.VISIBLE);
            return;
        }

        resultText.setText("Runda zavrsena. Sledecu rundu zapocinje Igrac 2.");
        roundText.postDelayed(() -> {
            if (isAdded() && !gameFinished) {
                roundIndex++;
                startRound();
            }
        }, 1200);
    }

    private void startTimer() {
        cancelTimer();
        timer = new CountDownTimer(TURN_DURATION_MS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timerText.setText((millisUntilFinished / 1000) + "s");
            }

            @Override
            public void onFinish() {
                timerText.setText("0s");
                resultText.setText("Vreme igraca " + (activePlayer + 1) + " je isteklo.");
                finishCurrentChance();
            }
        }.start();
    }

    private boolean allPairsSolved() {
        for (boolean pairSolved : solved) {
            if (!pairSolved) {
                return false;
            }
        }
        return true;
    }

    private boolean allRemainingPairsAttempted() {
        for (int i = 0; i < solved.length; i++) {
            if (!solved[i] && !attemptedLeft.contains(i)) {
                return false;
            }
        }
        return true;
    }

    private void enableUnsolvedPairs() {
        for (int i = 0; i < solved.length; i++) {
            if (!solved[i]) {
                leftButtons[i].setEnabled(true);
                leftButtons[i].setBackgroundColor(COLOR_DEFAULT);
            }
        }
    }

    private void clearSelectedLeft() {
        if (selectedLeftIndex >= 0 && !solved[selectedLeftIndex]) {
            leftButtons[selectedLeftIndex].setBackgroundColor(COLOR_DEFAULT);
        }
        selectedLeftIndex = -1;
    }

    private void updateScores() {
        playerOneScoreText.setText("Igrac 1: " + scores[0]);
        playerTwoScoreText.setText("Igrac 2: " + scores[1]);
    }

    private void updateTurnText() {
        String chance = secondChance ? " (preostali parovi)" : " (pocinje rundu)";
        turnText.setText("Na potezu: Igrac " + (activePlayer + 1) + chance);
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

    @Override
    public void onDestroyView() {
        cancelTimer();
        super.onDestroyView();
    }
}
