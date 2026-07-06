package com.example.slagalicatim04.fragments;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import com.example.slagalicatim04.regions.RegionInfo;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

public class RegionChallengesFragment extends Fragment {
    public static final String ARG_REGION_KEY = "regionKey";

    private final RegionChallengeRepository repository = new RegionChallengeRepository();

    private AuthUser currentUser;
    private RegionInfo region;
    private ListenerRegistration registration;
    private TextView regionTitle;
    private EditText starsInput;
    private EditText tokensInput;
    private LinearLayout listContainer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_region_challenges, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        currentUser = AuthService.getInstance(requireContext()).getCurrentUser();
        String regionKey = getArguments() == null
                ? ""
                : getArguments().getString(ARG_REGION_KEY, "");
        region = !isEmpty(regionKey)
                ? RegionInfo.byName(regionKey)
                : RegionInfo.byName(currentUser == null ? "" : currentUser.getRegion());

        regionTitle = view.findViewById(R.id.regionChallengeTitle);
        starsInput = view.findViewById(R.id.regionChallengeStarsInput);
        tokensInput = view.findViewById(R.id.regionChallengeTokensInput);
        listContainer = view.findViewById(R.id.regionChallengeList);
        regionTitle.setText("Izazovi regiona: " + region.name);
        view.findViewById(R.id.regionChallengeCreateButton).setOnClickListener(v -> createChallenge());
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
        registration = repository.listen(region.key, new RegionChallengeRepository.Listener() {
            @Override
            public void onChallenges(List<RegionChallenge> challenges) {
                if (!isAdded()) {
                    return;
                }
                renderChallenges(challenges);
            }

            @Override
            public void onError(Exception error) {
                showError(error);
            }
        });
    }

    private void createChallenge() {
        long stars = parseNumber(starsInput);
        long tokens = parseNumber(tokensInput);
        repository.createChallenge(currentUser, stars, tokens,
                () -> {
                    clearInputs();
                    refreshCurrentUser();
                    showToast("Izazov je postavljen.");
                },
                this::showError);
    }

    private void renderChallenges(List<RegionChallenge> challenges) {
        listContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        if (challenges == null || challenges.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("Nema aktivnih ni zavrsenih izazova za ovaj region.");
            empty.setTextColor(0xFF5F5A73);
            empty.setTextSize(15f);
            listContainer.addView(empty);
            return;
        }
        for (RegionChallenge challenge : challenges) {
            View card = inflater.inflate(R.layout.item_region_challenge, listContainer, false);
            bindChallenge(card, challenge);
            listContainer.addView(card);
        }
    }

    private void bindChallenge(View card, RegionChallenge challenge) {
        TextView title = card.findViewById(R.id.regionChallengeItemTitle);
        TextView meta = card.findViewById(R.id.regionChallengeItemMeta);
        TextView status = card.findViewById(R.id.regionChallengeItemStatus);
        TextView participants = card.findViewById(R.id.regionChallengeItemParticipants);
        EditText scoreInput = card.findViewById(R.id.regionChallengeScoreInput);
        MaterialButton enterButton = card.findViewById(R.id.regionChallengeEnterButton);
        MaterialButton joinButton = card.findViewById(R.id.regionChallengeJoinButton);
        MaterialButton submitButton = card.findViewById(R.id.regionChallengeSubmitButton);

        title.setText(challenge.creatorName + " postavlja izazov");
        meta.setText(metaText(challenge));
        status.setText(statusText(challenge));
        participants.setText(participantsText(challenge));
        scoreInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        scoreInput.setHint("Ukupni poeni");

        joinButton.setVisibility(challenge.canJoin(currentUserId()) ? View.VISIBLE : View.GONE);
        enterButton.setVisibility(challenge.hasParticipant(currentUserId()) ? View.VISIBLE : View.GONE);
        submitButton.setVisibility(View.GONE);
        scoreInput.setVisibility(View.GONE);

        joinButton.setOnClickListener(v -> repository.joinChallenge(currentUser, challenge.id,
                () -> {
                    refreshCurrentUser();
                    showToast("Prihvatio si izazov.");
                },
                this::showError));
        enterButton.setOnClickListener(v -> openChallengeRoom(challenge.id));
    }

    private String statusText(RegionChallenge challenge) {
        if (challenge.isOpen()) {
            return "Otvoren izazov";
        }
        if (challenge.isActive()) {
            return "Izazov je u toku";
        }
        return "Izazov je zavrsen";
    }

    private String metaText(RegionChallenge challenge) {
        String base = "Ulog: " + challenge.stakeStars + " zvezda, " + challenge.stakeTokens
                + " tokena • Igraci: " + challenge.participantCount() + "/" + challenge.maxPlayers;
        if (challenge.isOpen()) {
            if (challenge.participantCount() >= 2) {
                return base + "\nIgraci mogu uci u partiju, a vlasnik kasnije zatvara izazov.";
            }
            return base + "\nPotrebna su najmanje 2 igraca da bi izazov poceo.";
        }
        if (challenge.isActive()) {
            return base + "\nSvaki igrac posalje svoj konacni rezultat, a kreator zatvara izazov dugmetom Zavrsi izazov.";
        }
        return base + "\nRezultat je obracunat. Pobednik dobija 75% ukupnog uloga, drugi vraca svoj ulog.";
    }

    private String participantsText(RegionChallenge challenge) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < challenge.participants.size(); i++) {
            RegionChallengeParticipant participant = challenge.participants.get(i);
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(i + 1).append(". ").append(participant.username);
            if (participant.submitted) {
                builder.append(" • ").append(participant.score).append(" bodova");
            } else if (challenge.isActive()) {
                builder.append(" • ceka rezultat");
            }
            if (participant.starsAwarded > 0 || participant.tokensAwarded > 0) {
                builder.append(" • +").append(participant.starsAwarded).append(" zvezda");
                if (participant.tokensAwarded > 0) {
                    builder.append(", +").append(participant.tokensAwarded).append(" tokena");
                }
            }
        }
        return builder.toString();
    }

    private long parseNumber(EditText input) {
        if (input == null) {
            return 0L;
        }
        String value = input.getText() == null ? "" : input.getText().toString().trim();
        if (value.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            return 0L;
        }
    }

    private void clearInputs() {
        starsInput.setText("");
        tokensInput.setText("");
    }

    private void openChallengeRoom(String challengeId) {
        Bundle args = new Bundle();
        args.putString(RegionChallengeRoomFragment.ARG_CHALLENGE_ID, challengeId);
        Navigation.findNavController(requireView()).navigate(R.id.regionChallengeRoomFragment, args);
    }

    private void refreshCurrentUser() {
        new Thread(() -> AuthService.getInstance(requireContext()).refreshCurrentUser()).start();
    }

    private String currentUserId() {
        return currentUser == null ? "" : currentUser.getId();
    }

    private void showError(Exception error) {
        if (isAdded()) {
            String message = error == null || isEmpty(error.getMessage())
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

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
