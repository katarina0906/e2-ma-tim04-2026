package com.example.slagalicatim04.fragments;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.slagalicatim04.R;
import com.example.slagalicatim04.auth.AuthService;
import com.example.slagalicatim04.auth.AuthUser;
import com.example.slagalicatim04.regions.RegionChallenge;
import com.example.slagalicatim04.regions.RegionChallengeParticipant;
import com.example.slagalicatim04.regions.RegionChallengeRepository;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.ListenerRegistration;

public class RegionChallengeRoomFragment extends Fragment {
    public static final String ARG_CHALLENGE_ID = "challengeId";
    public static final String ARG_PREVIEW_SCORE = "previewScore";
    public static final String ARG_PREVIEW_USER_ID = "previewUserId";
    private final RegionChallengeRepository repository = new RegionChallengeRepository();

    private AuthUser currentUser;
    private String challengeId;
    private ListenerRegistration registration;
    private TextView titleText;
    private TextView metaText;
    private TextView participantsText;
    private EditText scoreInput;
    private MaterialButton playButton;
    private MaterialButton finishButton;
    private MaterialButton submitButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_region_challenge_room, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        currentUser = AuthService.getInstance(requireContext()).getCurrentUser();
        challengeId = getArguments() == null ? "" : getArguments().getString(ARG_CHALLENGE_ID, "");
        titleText = view.findViewById(R.id.regionChallengeRoomTitle);
        metaText = view.findViewById(R.id.regionChallengeRoomMeta);
        participantsText = view.findViewById(R.id.regionChallengeRoomParticipants);
        scoreInput = view.findViewById(R.id.regionChallengeRoomScoreInput);
        playButton = view.findViewById(R.id.regionChallengeRoomPlayButton);
        finishButton = view.findViewById(R.id.regionChallengeRoomFinishButton);
        submitButton = view.findViewById(R.id.regionChallengeRoomSubmitButton);
        scoreInput.setInputType(InputType.TYPE_CLASS_NUMBER);

        listen();
    }

    @Override
    public void onDestroyView() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        super.onDestroyView();
    }

    private void listen() {
        registration = repository.listenChallenge(challengeId, new RegionChallengeRepository.ChallengeListener() {
            @Override
            public void onChallenge(RegionChallenge challenge) {
                if (!isAdded()) {
                    return;
                }
                render(challenge);
            }

            @Override
            public void onError(Exception error) {
                showError(error);
            }
        });
    }

    private void render(RegionChallenge challenge) {
        titleText.setText(challenge.creatorName + " • izazov");
        metaText.setText(metaLine(challenge));
        participantsText.setText(participantsLine(challenge));

        boolean joined = challenge.hasParticipant(currentUserId());
        boolean canFinish = challenge.canFinish(currentUserId());

        playButton.setVisibility(joined && !challenge.isFinished() ? View.VISIBLE : View.GONE);
        finishButton.setVisibility(canFinish ? View.VISIBLE : View.GONE);
        submitButton.setVisibility(View.GONE);
        scoreInput.setVisibility(View.GONE);

        playButton.setOnClickListener(v -> openChallengeGame(challenge.id));
        finishButton.setOnClickListener(v -> repository.finishChallenge(currentUser, challenge.id,
                () -> openFinishedChallenge(challenge.id),
                this::showError));

        if (!joined) {
            metaText.setText("Nisi u ovom izazovu.");
            playButton.setVisibility(View.GONE);
            finishButton.setVisibility(View.GONE);
            submitButton.setVisibility(View.GONE);
            scoreInput.setVisibility(View.GONE);
        }
    }

    private String metaLine(RegionChallenge challenge) {
        String base = "Ulog: " + challenge.stakeStars + " zvezda, " + challenge.stakeTokens
                + " tokena • Igraci: " + challenge.participantCount() + "/" + challenge.maxPlayers;
        if (challenge.isOpen()) {
            return challenge.participantCount() >= 2
                    ? base + "\nUdji u partiju ili klikni Zavrsi izazov kad god hoces da zatvoris izazov."
                    : base + "\nUdji u partiju. Vlasnik moze zatvoriti izazov dugmetom Zavrsi izazov.";
        }
        if (challenge.isActive()) {
            return base + "\nIzazov je poceo. Udji u partiju, rezultat se cuva automatski."
                    + "\nKreator zatvara izazov dugmetom Zavrsi izazov.";
        }
        return base + "\nIzazov je zavrsen.\n" + finishedSummary(challenge);
    }

    private String participantsLine(RegionChallenge challenge) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < challenge.participants.size(); i++) {
            RegionChallengeParticipant participant = challenge.participants.get(i);
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(rankLabel(i + 1)).append(' ').append(participant.username);
            if (participant.submitted) {
                builder.append(" • zavrsio • ").append(participant.score).append(" bodova");
                if (challenge.isFinished()) {
                    builder.append(rewardSummary(participant));
                }
            } else if (challenge.isActive()) {
                builder.append(" • ").append(participant.score).append(" bodova • jos igra");
            } else {
                builder.append(" • ").append(participant.score).append(" bodova");
            }
        }
        return builder.toString();
    }

    private String rankLabel(int rank) {
        return "#" + rank;
    }

    private String rewardSummary(RegionChallengeParticipant participant) {
        StringBuilder builder = new StringBuilder();
        if (participant.starsAwarded > 0) {
            builder.append(" • +").append(participant.starsAwarded).append(" zvezda");
        }
        if (participant.tokensAwarded > 0) {
            builder.append(" • +").append(participant.tokensAwarded).append(" tokena");
        }
        if (builder.length() == 0) {
            builder.append(" • bez nagrade");
        }
        return builder.toString();
    }

    private String finishedSummary(RegionChallenge challenge) {
        if (!challenge.isFinished() || challenge.participants.isEmpty()) {
            return "Rezultat nije dostupan.";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Raspodela nagrada:\n");
        builder.append("1. mesto uzima 75% ukupnog uloga, 2. mesto dobija nazad svoj ulog.\n");
        for (int i = 0; i < challenge.participants.size(); i++) {
            RegionChallengeParticipant participant = challenge.participants.get(i);
            builder.append(i + 1)
                    .append(". ")
                    .append(participant.username)
                    .append(" - ")
                    .append(participant.score)
                    .append(" bodova")
                    .append(rewardSummary(participant));
            if (i < challenge.participants.size() - 1) {
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private String currentUserId() {
        return currentUser == null ? "" : currentUser.getId();
    }

    private void openChallengeGame(String challengeId) {
        if (getView() == null) {
            return;
        }
        repository.ensureSoloChallengeMatch(currentUser, challengeId, roomId -> {
            if (!isAdded() || getView() == null) {
                return;
            }
            Bundle args = new Bundle();
            args.putString("roomId", roomId);
            Navigation.findNavController(requireView()).navigate(R.id.koZnaZnaFragment, args);
        }, this::showError);
    }

    private void openFinishedChallenge(String challengeId) {
        if (!isAdded() || getView() == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putString("challengeId", challengeId);
        args.putBoolean("challengeSummary", true);
        Navigation.findNavController(requireView()).navigate(R.id.matchResultFragment, args);
    }

    private void showError(Exception error) {
        if (isAdded()) {
            String message = error == null || error.getMessage() == null || error.getMessage().trim().isEmpty()
                    ? "Operacija nije uspela."
                    : error.getMessage();
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
        }
    }

    private void showToast(String message) {
        if (isAdded()) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        }
    }
}
