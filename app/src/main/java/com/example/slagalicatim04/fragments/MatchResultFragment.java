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
import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.navigation.NavOptions;

import com.example.slagalicatim04.R;
import com.example.slagalicatim04.auth.AuthService;
import com.example.slagalicatim04.auth.AuthUser;
import com.example.slagalicatim04.auth.PlayerHeaderLoader;
import com.example.slagalicatim04.matchresult.MatchResultRepository;
import com.example.slagalicatim04.matchresult.MatchResultState;
import com.example.slagalicatim04.regions.RegionChallengeRepository;
import com.example.slagalicatim04.repositories.MultiplayerGameRepository;
import com.google.firebase.firestore.ListenerRegistration;

public class MatchResultFragment extends Fragment implements ExitConfirmationHandler {
    private static final String ARG_SOLO_PREVIEW_SCORE = "soloPreviewScore";
    private static final String ARG_SOLO_PREVIEW = "soloPreview";
    private static final String ARG_CHALLENGE_ID = "challengeId";
    private static final String ARG_PREVIEW_USER_ID = "previewUserId";

    private String roomId = MultiplayerGameRepository.TEST_ROOM_ID;
    private ListenerRegistration registration;
    private TextView winnerText;
    private TextView player1Name;
    private TextView player1Score;
    private TextView player2Name;
    private TextView player2Score;
    private TextView soloDescription;
    private View playersRow;
    private ImageView player1Avatar;
    private ImageView player2Avatar;
    private boolean releasedPlayers;
    private boolean submittedChallengeScore;
    private View homeButton;
    private boolean hasSoloPreview;
    private long soloPreviewScore;
    private String previewChallengeId = "";
    private String previewUserId = "";
    private boolean activeSoloPreview;

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
        if (getArguments() != null) {
            hasSoloPreview = getArguments().getBoolean(ARG_SOLO_PREVIEW, false);
            soloPreviewScore = getArguments().getLong(ARG_SOLO_PREVIEW_SCORE, 0L);
            previewChallengeId = getArguments().getString(ARG_CHALLENGE_ID, "");
            previewUserId = getArguments().getString(ARG_PREVIEW_USER_ID, "");
        }
        AuthUser currentUser = AuthService.getInstance(requireContext()).getCurrentUser();
        activeSoloPreview = hasSoloPreview
                && currentUser != null
                && !isEmpty(previewUserId)
                && previewUserId.equals(currentUser.getId());
        winnerText = view.findViewById(R.id.matchResultWinner);
        player1Name = view.findViewById(R.id.matchResultPlayer1Name);
        player1Score = view.findViewById(R.id.matchResultPlayer1Score);
        player2Name = view.findViewById(R.id.matchResultPlayer2Name);
        player2Score = view.findViewById(R.id.matchResultPlayer2Score);
        soloDescription = view.findViewById(R.id.matchResultSoloDescription);
        playersRow = view.findViewById(R.id.matchResultPlayersRow);
        player1Avatar = view.findViewById(R.id.matchResultPlayer1Avatar);
        player2Avatar = view.findViewById(R.id.matchResultPlayer2Avatar);
        homeButton = view.findViewById(R.id.matchResultHomeButton);
        if (playersRow != null) {
            playersRow.setVisibility(View.GONE);
        }
        if (homeButton instanceof TextView) {
            ((TextView) homeButton).setText(activeSoloPreview ? "Nazad na izazov" : "Nazad na pocetnu");
        }
        homeButton.setOnClickListener(v -> persistPreviewAndNavigate(v, activeSoloPreview, previewChallengeId));
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(), new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        persistPreviewAndNavigate(view, activeSoloPreview, previewChallengeId);
                    }
                });
        renderSoloPreview();

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

    private void renderSoloPreview() {
        if (!activeSoloPreview) {
            return;
        }
        savePreviewScoreToChallenge();
        updateHeaderVisibility(true);
        winnerText.setText("Samostalna partija je zavrsena. Osvojili ste "
                + soloPreviewScore + " bodova.");
        submitPreviewChallengeScore(null);
    }

    private void render(MatchResultState state) {
        if (!state.isMatchResult() || !isAdded()) {
            return;
        }
        String firstName = playerName(state.getPlayer1Name(), "Igrac 1");
        String secondName = playerName(state.getPlayer2Name(), "Igrac 2");
        updateHeaderVisibility(state.isSoloChallenge());
        bindExitAction(state);
        player1Name.setText(firstName);
        player1Score.setText(scoreSummary(state.getPlayer1Score(), state.getPlayer1StarDelta(),
                state.getPlayer1EarnedTokens(), state.isSoloChallenge()));
        PlayerHeaderLoader.loadAvatar(state.getPlayer1Id(), player1Avatar);
        if (!state.isSoloChallenge()) {
            player2Name.setText(secondName);
            player2Score.setText(scoreSummary(state.getPlayer2Score(), state.getPlayer2StarDelta(),
                    state.getPlayer2EarnedTokens(), false));
            PlayerHeaderLoader.loadAvatar(state.getPlayer2Id(), player2Avatar);
        }
        releasePlayers(state);
        if (state.isSoloChallenge()) {
            winnerText.setText(soloSummary(state));
        } else if (state.winner() == 1) {
            winnerText.setText("Pobednik je " + firstName + "!");
        } else if (state.winner() == 2) {
            winnerText.setText("Pobednik je " + secondName + "!");
        } else {
            winnerText.setText("Partija je zavrsena nereseno.");
        }
        submitChallengeScore(state);
    }

    private void updateHeaderVisibility(boolean soloChallenge) {
        if (playersRow != null) {
            playersRow.setVisibility(soloChallenge ? View.GONE : View.VISIBLE);
        }
        if (soloDescription == null) {
            return;
        }
        soloDescription.setVisibility(View.GONE);
        int visibility = soloChallenge ? View.GONE : View.VISIBLE;
        player2Name.setVisibility(visibility);
        player2Score.setVisibility(visibility);
        player2Avatar.setVisibility(visibility);
    }

    private void bindExitAction(MatchResultState state) {
        if (homeButton == null) {
            return;
        }
        if (homeButton instanceof TextView) {
            ((TextView) homeButton).setText(
                    state.isSoloChallenge() ? "Nazad na izazov" : "Nazad na pocetnu");
        }
        homeButton.setOnClickListener(v -> persistPreviewAndNavigate(v, state.isSoloChallenge(), state.getChallengeId()));
    }

    private void releasePlayers(MatchResultState state) {
        if (releasedPlayers) {
            return;
        }
        releasedPlayers = true;
        new Thread(() -> {
            try {
                new MatchResultRepository().releasePlayers(
                        roomId,
                        state.getPlayer1Id(),
                        state.getPlayer2Id());
            } catch (Exception ignored) {
                releasedPlayers = false;
            }
        }).start();
    }

    private void submitChallengeScore(MatchResultState state) {
        if (submittedChallengeScore || !state.isSoloChallenge() || isEmpty(state.getChallengeId())) {
            return;
        }
        AuthUser currentUser = AuthService.getInstance(requireContext()).getCurrentUser();
        if (currentUser == null) {
            return;
        }
        submittedChallengeScore = true;
        new RegionChallengeRepository().submitScore(
                currentUser,
                state.getChallengeId(),
                state.getPlayer1Score(),
                () -> { },
                error -> submittedChallengeScore = error != null
                        && error.getMessage() != null
                        && error.getMessage().contains("vec poslat")
                        ? true : false);
    }

    private void submitPreviewChallengeScore(@Nullable Runnable afterSubmit) {
        if (!activeSoloPreview || isEmpty(previewChallengeId) || submittedChallengeScore) {
            if (afterSubmit != null) {
                afterSubmit.run();
            }
            return;
        }
        AuthUser currentUser = AuthService.getInstance(requireContext()).getCurrentUser();
        if (currentUser == null) {
            if (afterSubmit != null) {
                afterSubmit.run();
            }
            return;
        }
        submittedChallengeScore = true;
        new RegionChallengeRepository().submitScore(
                currentUser,
                previewChallengeId,
                soloPreviewScore,
                () -> {
                    if (afterSubmit != null && isAdded()) {
                        requireActivity().runOnUiThread(afterSubmit);
                    }
                },
                error -> {
                    boolean alreadySubmitted = error != null
                            && error.getMessage() != null
                            && error.getMessage().contains("vec poslat");
                    submittedChallengeScore = alreadySubmitted;
                    if (!alreadySubmitted) {
                        submittedChallengeScore = false;
                    }
                    if (afterSubmit != null && isAdded()) {
                        requireActivity().runOnUiThread(afterSubmit);
                    }
                });
    }

    private void savePreviewScoreToChallenge() {
        if (!activeSoloPreview || isEmpty(previewChallengeId)) {
            return;
        }
        AuthUser currentUser = AuthService.getInstance(requireContext()).getCurrentUser();
        if (currentUser == null) {
            return;
        }
        new RegionChallengeRepository().savePreviewScore(
                currentUser,
                previewChallengeId,
                soloPreviewScore,
                () -> { },
                error -> { });
    }

    private String playerName(String name, String fallback) {
        return isEmpty(name) ? fallback : name;
    }

    private String scoreSummary(long score, long starDelta, long earnedTokens, boolean soloChallenge) {
        StringBuilder builder = new StringBuilder();
        builder.append(score).append(" bodova");
        if (soloChallenge) {
            return builder.toString();
        }
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

    private String soloSummary(MatchResultState state) {
        return "Samostalna partija je zavrsena. Osvojili ste " + state.getPlayer1Score()
                + " bodova.";
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void navigateAway(View source, boolean soloChallenge, String challengeId) {
        if (soloChallenge && !isEmpty(challengeId)) {
            Bundle args = new Bundle();
            args.putString(RegionChallengeRoomFragment.ARG_CHALLENGE_ID, challengeId);
            if (activeSoloPreview) {
                args.putLong(RegionChallengeRoomFragment.ARG_PREVIEW_SCORE, soloPreviewScore);
                args.putString(RegionChallengeRoomFragment.ARG_PREVIEW_USER_ID, previewUserId);
            }
            Navigation.findNavController(source).navigate(
                    R.id.regionChallengeRoomFragment,
                    args,
                    new NavOptions.Builder()
                            .setLaunchSingleTop(true)
                            .build());
            return;
        }
        Navigation.findNavController(source).navigate(
                R.id.homeFragment,
                null,
                new NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .setPopUpTo(R.id.homeFragment, false)
                        .build());
    }

    private void persistPreviewAndNavigate(View source, boolean soloChallenge, String challengeId) {
        if (soloChallenge && activeSoloPreview && !isEmpty(previewChallengeId)) {
            submitPreviewChallengeScore(() -> navigateAway(source, true, previewChallengeId));
            return;
        }
        navigateAway(source, soloChallenge, challengeId);
    }

    @Override
    public boolean handleExitRequest() {
        if (getView() == null) {
            return false;
        }
        persistPreviewAndNavigate(requireView(), activeSoloPreview, previewChallengeId);
        return true;
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
