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
import com.example.slagalicatim04.models.Answer;
import com.example.slagalicatim04.models.Question;
import com.example.slagalicatim04.models.QuizMultiplayerState;
import com.example.slagalicatim04.repositories.LocalQuizRepository;
import com.example.slagalicatim04.repositories.MatchForfeitRepository;
import com.example.slagalicatim04.repositories.MultiplayerGameRepository;

import java.util.List;

public class KoZnaZnaFragment extends Fragment implements ExitConfirmationHandler {

    private static final long QUESTION_DURATION_MS = 5_000L;
    private static final int COLOR_DEFAULT = Color.rgb(103, 80, 164);
    private static final int COLOR_SELECTED = Color.rgb(249, 168, 37);
    private static final int COLOR_FORFEITED = Color.RED;
    private static final int COLOR_SCORE_DEFAULT = Color.BLACK;

    private TextView timerText;
    private TextView resultText;
    private TextView playerScoreText;
    private TextView opponentScoreText;
    private ImageView playerOneAvatar;
    private ImageView playerTwoAvatar;
    private TextView questionText;
    private TextView questionCounterText;
    private Button[] answerButtons;
    private CountDownTimer timer;

    private List<Question> questions;
    private MultiplayerGameRepository multiplayerRepository;
    private MultiplayerGameRepository.Subscription stateRegistration;
    private QuizMultiplayerState currentState;
    private String roomId = MultiplayerGameRepository.TEST_ROOM_ID;
    private MatchForfeitRepository forfeitRepository;
    private int renderedQuestion = -1;
    private int timerQuestion = -1;
    private boolean navigatedToSpojnice;
    private String lastKnownPlayer1Label = "Igrac 1";
    private String lastKnownPlayer2Label = "Igrac 2";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_ko_zna_zna, container, false);

        timerText = view.findViewById(R.id.timerText);
        resultText = view.findViewById(R.id.resultText);
        playerScoreText = view.findViewById(R.id.kzzScore0);
        opponentScoreText = view.findViewById(R.id.kzzScore1);
        playerOneAvatar = view.findViewById(R.id.kzzAvatar0);
        playerTwoAvatar = view.findViewById(R.id.kzzAvatar1);
        questionText = view.findViewById(R.id.questionText);
        questionCounterText = view.findViewById(R.id.questionCounterText);
        answerButtons = new Button[]{
                view.findViewById(R.id.answerA),
                view.findViewById(R.id.answerB),
                view.findViewById(R.id.answerC),
                view.findViewById(R.id.answerD)
        };

        if (getArguments() != null && !isEmpty(getArguments().getString("roomId"))) {
            roomId = getArguments().getString("roomId");
        }
        questions = new LocalQuizRepository().getQuestions();
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

        for (int index = 0; index < answerButtons.length; index++) {
            int answerIndex = index;
            answerButtons[index].setOnClickListener(v -> submitAnswer(answerIndex));
        }

        showWaitingState();
        stateRegistration = multiplayerRepository.joinQuiz(
                new MultiplayerGameRepository.StateListener<QuizMultiplayerState>() {
                    @Override
                    public void onState(QuizMultiplayerState state) {
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

    private void renderState(QuizMultiplayerState state) {
        currentState = state;
        updateScores(state);

        if ("next".equals(state.getStatus())) {
            navigateToSpojnice();
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

        int questionIndex = state.getCurrentQuestion();
        if (questionIndex < 0 || questionIndex >= questions.size()) {
            return;
        }
        if (renderedQuestion != questionIndex) {
            renderedQuestion = questionIndex;
            showQuestion(questionIndex);
            resultText.setText("");
        }

        boolean answered = state.hasAnswered(multiplayerRepository.getPlayerId());
        if (state.hasForfeit()) {
            resultText.setText(nonEmpty(state.getStatusMessage(),
                    "Protivnik je napustio partiju. Nastavljas bez cekanja."));
        }
        setButtonsEnabled(!answered);
        if (answered && !state.hasForfeit()) {
            resultText.setText("Odgovor je poslat. Ceka se drugi igrac.");
        }
        int requiredAnswers = (state.isForfeited(state.getPlayer1Id())
                || state.isForfeited(state.getPlayer2Id())) ? 1 : 2;
        if (state.getAnswerCount() >= requiredAnswers) {
            multiplayerRepository.advanceQuizIfReady(questionIndex);
        }
        startTimerForQuestion(questionIndex);
    }

    private void showQuestion(int questionIndex) {
        Question question = questions.get(questionIndex);
        questionCounterText.setText("Pitanje " + (questionIndex + 1) + " / " + questions.size());
        questionText.setText(question.getText());

        List<Answer> answers = question.getAnswers();
        for (int index = 0; index < answerButtons.length; index++) {
            answerButtons[index].setVisibility(View.VISIBLE);
            answerButtons[index].setText(answers.get(index).getText());
            answerButtons[index].setBackgroundColor(COLOR_DEFAULT);
            answerButtons[index].setTextColor(Color.WHITE);
        }
    }

    private void submitAnswer(int answerIndex) {
        if (currentState == null || !"playing".equals(currentState.getStatus())
                || currentState.hasAnswered(multiplayerRepository.getPlayerId())) {
            return;
        }

        Question question = questions.get(currentState.getCurrentQuestion());
        Answer answer = question.getAnswers().get(answerIndex);
        answerButtons[answerIndex].setBackgroundColor(COLOR_SELECTED);
        setButtonsEnabled(false);
        resultText.setText("Slanje odgovora...");
        multiplayerRepository.submitQuizAnswer(currentState.getCurrentQuestion(),
                answer.getId(), answer.isCorrect());
    }

    private void startTimerForQuestion(int questionIndex) {
        if (timerQuestion == questionIndex && timer != null) {
            return;
        }
        cancelTimer();
        timerQuestion = questionIndex;

        timer = new CountDownTimer(QUESTION_DURATION_MS, 250L) {
            @Override
            public void onTick(long millisUntilFinished) {
                timerText.setText(Math.max(1L, (millisUntilFinished + 999L) / 1000L) + "s");
            }

            @Override
            public void onFinish() {
                timerText.setText("0s");
                setButtonsEnabled(false);
                multiplayerRepository.expireQuizQuestion(questionIndex);
            }
        }.start();
    }

    private void updateScores(QuizMultiplayerState state) {
        updateHeaderVisibility(state.isSoloChallenge());
        lastKnownPlayer1Label = resolvePlayerLabel(state.getPlayer1Id(), state.getPlayer1Name(),
                lastKnownPlayer1Label, "Igrac 1");
        lastKnownPlayer2Label = resolvePlayerLabel(state.getPlayer2Id(), state.getPlayer2Name(),
                lastKnownPlayer2Label, "Igrac 2");
        playerScoreText.setText(lastKnownPlayer1Label + ": "
                + state.getScore(state.getPlayer1Id()));
        opponentScoreText.setText(lastKnownPlayer2Label + ": "
                + state.getScore(state.getPlayer2Id()));
        playerScoreText.setTextColor(state.isForfeited(state.getPlayer1Id())
                ? COLOR_FORFEITED : COLOR_SCORE_DEFAULT);
        opponentScoreText.setTextColor(state.isForfeited(state.getPlayer2Id())
                ? COLOR_FORFEITED : COLOR_SCORE_DEFAULT);
        PlayerHeaderLoader.loadAvatar(state.getPlayer1Id(), playerOneAvatar);
        if (!state.isSoloChallenge()) {
            PlayerHeaderLoader.loadAvatar(state.getPlayer2Id(), playerTwoAvatar);
        }
    }

    private void updateHeaderVisibility(boolean soloChallenge) {
        int visibility = soloChallenge ? View.GONE : View.VISIBLE;
        opponentScoreText.setVisibility(visibility);
        playerTwoAvatar.setVisibility(visibility);
    }

    private String resolvePlayerLabel(String playerId, String name, String previousLabel,
                                      String fallback) {
        if (name != null && !name.trim().isEmpty()) {
            return name;
        }
        if (playerId != null && !playerId.trim().isEmpty()) {
            return playerId;
        }
        return previousLabel == null || previousLabel.trim().isEmpty() ? fallback : previousLabel;
    }

    private void showWaitingState() {
        showWaitingState(null);
    }

    private void showWaitingState(QuizMultiplayerState state) {
        cancelTimer();
        timerText.setText("5s");
        questionCounterText.setText("");
        boolean opponentLeft = state != null && state.hasForfeit();
        String persistedStatus = state == null ? "" : state.getStatusMessage();
        boolean hasPersistedStatus = !isEmpty(persistedStatus);
        boolean hasState = state != null;
        questionText.setText(opponentLeft
                ? "Protivnik je napustio partiju."
                : ((hasPersistedStatus || hasState)
                ? "Stanje partije je sacuvano."
                : "Ceka se drugi igrac..."));
        resultText.setText(hasPersistedStatus
                ? persistedStatus
                : (hasState
                ? "Partija je aktivna. Sacekaj sledece stanje iz baze."
                : "Oba uredjaja treba da otvore igru Ko zna zna."));
        setButtonsEnabled(false);
    }

    private void finishGame(QuizMultiplayerState state) {
        cancelTimer();
        timerText.setText("0s");
        questionCounterText.setText("Kraj igre");
        questionText.setText("Ko zna zna je zavrsena!");
        resultText.setText(state.isSoloChallenge()
                ? "Rezultat: " + state.getScore(state.getPlayer1Id())
                : "Igrac 1: " + state.getScore(state.getPlayer1Id())
                + " | Igrac 2: " + state.getScore(state.getPlayer2Id()));
        setButtonsEnabled(false);
    }

    private void showError(Exception error) {
        cancelTimer();
        setButtonsEnabled(false);
        resultText.setText("Firestore greska: " + error.getMessage());
    }

    private void setButtonsEnabled(boolean enabled) {
        if (answerButtons == null) {
            return;
        }
        for (Button button : answerButtons) {
            button.setEnabled(enabled);
        }
    }

    private void cancelTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private void navigateToSpojnice() {
        if (navigatedToSpojnice || getView() == null) {
            return;
        }
        navigatedToSpojnice = true;
        Bundle args = new Bundle();
        args.putString("roomId", roomId);
        Navigation.findNavController(requireView()).navigate(R.id.spojniceFragment, args);
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
        timerQuestion = -1;
        if (multiplayerRepository != null) {
            multiplayerRepository.leaveQuizWaitingRoom();
        }
        if (stateRegistration != null) {
            stateRegistration.remove();
            stateRegistration = null;
        }
        if (!navigatedToSpojnice) {
            new GameSessionRepository().abandonRoom(roomId);
        }
        super.onDestroyView();
    }
}
