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
import com.example.slagalicatim04.mynumber.MyNumberRepository;
import com.example.slagalicatim04.regions.RegionChallenge;
import com.example.slagalicatim04.regions.RegionChallengeParticipant;
import com.example.slagalicatim04.regions.RegionChallengeRepository;
import com.example.slagalicatim04.repositories.MultiplayerGameRepository;
import com.example.slagalicatim04.tournament.TournamentRepository;
import com.google.firebase.firestore.ListenerRegistration;

public class MatchResultFragment extends Fragment implements ExitConfirmationHandler {
    private static final String ARG_SOLO_PREVIEW_SCORE = "soloPreviewScore";
    private static final String ARG_SOLO_PREVIEW = "soloPreview";
    private static final String ARG_CHALLENGE_ID = "challengeId";
    private static final String ARG_PREVIEW_USER_ID = "previewUserId";
    private static final String ARG_CHALLENGE_SUMMARY = "challengeSummary";

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
    private boolean challengeSummaryMode;
    private boolean submittedTournamentResult;
    private String currentTournamentId = "";

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
            challengeSummaryMode = getArguments().getBoolean(ARG_CHALLENGE_SUMMARY, false);
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
            ((TextView) homeButton).setText(navigationLabel());
        }
        homeButton.setOnClickListener(v -> persistPreviewAndNavigate(v, activeSoloPreview, previewChallengeId));
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(), new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        persistPreviewAndNavigate(view, activeSoloPreview, previewChallengeId);
                    }
                });
        if (challengeSummaryMode) {
            listenChallengeSummary();
            return;
        }
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

    private void listenChallengeSummary() {
        if (isEmpty(previewChallengeId)) {
            winnerText.setText("Rezultat izazova nije dostupan.");
            return;
        }
        updateHeaderVisibility(true);
        if (soloDescription != null) {
            soloDescription.setVisibility(View.VISIBLE);
        }
        winnerText.setText("Obracun rezultata izazova...");
        registration = new RegionChallengeRepository().listenChallenge(previewChallengeId,
                new RegionChallengeRepository.ChallengeListener() {
                    @Override
                    public void onChallenge(RegionChallenge challenge) {
                        renderChallengeSummary(challenge);
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

    private void render(MatchResultState state) {
        if (!state.isMatchResult() || !isAdded()) {
            return;
        }
        String firstName = playerName(state.getPlayer1Name(), "Igrac 1");
        String secondName = playerName(state.getPlayer2Name(), "Igrac 2");
        boolean tournamentMatch = isTournamentMatch(state);
        if (tournamentMatch) {
            currentTournamentId = state.getTournamentId();
        }
        updateHeaderVisibility(state.isSoloChallenge());
        bindExitAction(state);
        player1Name.setText(firstName);
        player1Score.setText(scoreSummary(state.getPlayer1Score(), state.getPlayer1StarDelta(),
                state.getPlayer1EarnedTokens(), state.isSoloChallenge() || tournamentMatch));
        PlayerHeaderLoader.loadAvatar(state.getPlayer1Id(), player1Avatar);
        if (!state.isSoloChallenge()) {
            player2Name.setText(secondName);
            player2Score.setText(scoreSummary(state.getPlayer2Score(), state.getPlayer2StarDelta(),
                    state.getPlayer2EarnedTokens(), tournamentMatch));
            PlayerHeaderLoader.loadAvatar(state.getPlayer2Id(), player2Avatar);
        }
        releasePlayers(state);
        if (state.isSoloChallenge()) {
            winnerText.setText(soloSummary(state));
        } else if (state.winner() == 1) {
            winnerText.setText("Pobednik je " + firstName + "!");
            new MyNumberRepository(roomId).awardTokensIfFinished();
        } else if (state.winner() == 2) {
            winnerText.setText("Pobednik je " + secondName + "!");
            new MyNumberRepository(roomId).awardTokensIfFinished();
        } else {
            winnerText.setText("Partija je zavrsena nereseno.");
        }
        submitChallengeScore(state);
        submitTournamentResult(state);
    }

    private void submitTournamentResult(MatchResultState state) {
        if (submittedTournamentResult || state.winner() == 0 || !roomId.startsWith("tournament_")) {
            return;
        }
        submittedTournamentResult = true;
        new TournamentRepository().onMatchFinished(roomId, state);
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
        if (challengeSummaryMode) {
            return;
        }
        if (homeButton == null) {
            return;
        }
        if (homeButton instanceof TextView) {
            ((TextView) homeButton).setText(resultNavigationLabel(state));
        }
        if (isTournamentMatch(state)) {
            homeButton.setOnClickListener(v -> handleTournamentExit(v, state));
            return;
        }
        homeButton.setOnClickListener(v -> persistPreviewAndNavigate(v, state.isSoloChallenge(), state.getChallengeId()));
    }

    private String resultNavigationLabel(MatchResultState state) {
        if (isTournamentMatch(state)
                && "semifinal".equals(state.getTournamentRound())
                && isCurrentUserWinner(state)) {
            return "Igraj finale";
        }
        if (isTournamentMatch(state)) {
            return "Vrati se na turnir";
        }
        return navigationLabel();
    }

    private void handleTournamentExit(View source, MatchResultState state) {
        if ("semifinal".equals(state.getTournamentRound()) && isCurrentUserWinner(state)) {
            openFinalWhenReady(source, state.getTournamentId());
            return;
        }
        navigateToTournament(source, state.getTournamentId());
    }

    private boolean isCurrentUserWinner(MatchResultState state) {
        AuthUser currentUser = AuthService.getInstance(requireContext()).getCurrentUser();
        if (currentUser == null || state.winner() == 0) {
            return false;
        }
        String winnerId = state.winner() == 1 ? state.getPlayer1Id() : state.getPlayer2Id();
        return currentUser.getId().equals(winnerId);
    }

    private void openFinalWhenReady(View source, String tournamentId) {
        AuthUser currentUser = AuthService.getInstance(requireContext()).getCurrentUser();
        if (currentUser == null) {
            navigateToTournament(source, tournamentId);
            return;
        }
        new TournamentRepository().findFinalRoom(tournamentId, currentUser.getId(),
                new TournamentRepository.FinalRoomCallback() {
                    @Override
                    public void onReady(String roomId) {
                        if (!isAdded()) {
                            return;
                        }
                        Bundle args = new Bundle();
                        args.putString("roomId", roomId);
                        Navigation.findNavController(source)
                                .navigate(R.id.stepByStepWaitingRoomFragment, args);
                    }

                    @Override
                    public void onPending() {
                        if (!isAdded()) {
                            return;
                        }
                        Toast.makeText(requireContext(), "Ceka se drugi finalista.", Toast.LENGTH_LONG).show();
                        navigateToTournament(source, tournamentId);
                    }

                    @Override
                    public void onError(Exception error) {
                        if (!isAdded()) {
                            return;
                        }
                        Toast.makeText(requireContext(), error.getMessage(), Toast.LENGTH_LONG).show();
                        navigateToTournament(source, tournamentId);
                    }
                });
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

    private void renderChallengeSummary(RegionChallenge challenge) {
        if (!isAdded()) {
            return;
        }
        updateHeaderVisibility(true);
        winnerText.setText("Izazov je zavrsen");
        if (soloDescription != null) {
            soloDescription.setVisibility(View.VISIBLE);
            soloDescription.setText(challengeSummaryText(challenge));
        }
        if (homeButton instanceof TextView) {
            ((TextView) homeButton).setText("Nazad na izazov");
        }
    }

    private String challengeSummaryText(RegionChallenge challenge) {
        if (!challenge.isFinished() || challenge.participants.isEmpty()) {
            return "Rezultat izazova jos nije spreman.";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("1. mesto uzima 75% ukupnog uloga, 2. mesto dobija nazad svoj ulog.\n\n");
        for (int i = 0; i < challenge.participants.size(); i++) {
            RegionChallengeParticipant participant = challenge.participants.get(i);
            builder.append("#")
                    .append(i + 1)
                    .append(" ")
                    .append(participant.username)
                    .append(" - ")
                    .append(participant.score)
                    .append(" bodova");
            String reward = challengeRewardText(participant);
            if (!isEmpty(reward)) {
                builder.append(" • ").append(reward);
            }
            if (i < challenge.participants.size() - 1) {
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private String challengeRewardText(RegionChallengeParticipant participant) {
        StringBuilder builder = new StringBuilder();
        if (participant.starsAwarded > 0) {
            builder.append("+").append(participant.starsAwarded).append(" zvezda");
        }
        if (participant.tokensAwarded > 0) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append("+").append(participant.tokensAwarded).append(" tokena");
        }
        if (builder.length() == 0) {
            builder.append("bez nagrade");
        }
        return builder.toString();
    }

    private String navigationLabel() {
        if (!isEmpty(currentTournamentId)) {
            return "Nazad na turnir";
        }
        return (activeSoloPreview || challengeSummaryMode) ? "Nazad na izazov" : "Nazad na pocetnu";
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void navigateAway(View source, boolean soloChallenge, String challengeId) {
        if (!isEmpty(currentTournamentId)) {
            navigateToTournament(source, currentTournamentId);
            return;
        }
        if ((soloChallenge || challengeSummaryMode) && !isEmpty(challengeId)) {
            Bundle args = new Bundle();
            args.putString(RegionChallengeRoomFragment.ARG_CHALLENGE_ID, challengeId);
            if (activeSoloPreview && !challengeSummaryMode) {
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
        if (!isEmpty(currentTournamentId)) {
            navigateToTournament(source, currentTournamentId);
            return;
        }
        if (challengeSummaryMode) {
            navigateAway(source, true, previewChallengeId);
            return;
        }
        if (soloChallenge && activeSoloPreview && !isEmpty(previewChallengeId)) {
            submitPreviewChallengeScore(() -> navigateAway(source, true, previewChallengeId));
            return;
        }
        navigateAway(source, soloChallenge, challengeId);
    }

    private boolean isTournamentMatch(MatchResultState state) {
        return roomId.startsWith("tournament_") || !isEmpty(state.getTournamentId());
    }

    private void navigateToTournament(View source, String tournamentId) {
        Bundle args = new Bundle();
        args.putString(TournamentFragment.ARG_TOURNAMENT_ID, tournamentId);
        Navigation.findNavController(source).navigate(
                R.id.tournamentFragment,
                args,
                new NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .build());
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
