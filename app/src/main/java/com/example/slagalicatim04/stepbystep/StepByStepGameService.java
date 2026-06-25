package com.example.slagalicatim04.stepbystep;

import java.text.Normalizer;
import java.util.Locale;

public class StepByStepGameService {
    public static final int ROUND_DURATION_MS = 70_000;
    public static final int STEAL_DURATION_MS = 10_000;

    public int openedSteps(StepByStepMatchState state) {
        if (state.getVisibleStepCount() > 0) {
            return state.getVisibleStepCount();
        }
        return computedOpenedSteps(state);
    }

    public int computedOpenedSteps(StepByStepMatchState state) {
        String phase = state.effectivePhase();
        if (StepByStepMatchState.PHASE_WAITING.equals(phase)) {
            return 0;
        }
        if (isStealPhase(phase)) {
            return 7;
        }
        if (!isRoundPhase(phase)) {
            return 7;
        }
        int elapsedSeconds = Math.max(0, (ROUND_DURATION_MS / 1000) - state.getSecondsLeft());
        return Math.max(1, Math.min(7, 1 + (elapsedSeconds / 10)));
    }

    public int nextVisibleStepCount(StepByStepMatchState state, int nextSecondsLeft) {
        if (isStealPhase(state.effectivePhase())) {
            return 7;
        }
        if (!isRoundPhase(state.effectivePhase())) {
            return state.getVisibleStepCount();
        }
        int elapsedSeconds = Math.max(0, (ROUND_DURATION_MS / 1000) - nextSecondsLeft);
        return Math.max(1, Math.min(7, 1 + (elapsedSeconds / 10)));
    }

    public int nextSecondsLeft(StepByStepMatchState state) {
        if (!isRoundPhase(state.effectivePhase()) && !isStealPhase(state.effectivePhase())) {
            return state.getSecondsLeft();
        }
        return Math.max(0, state.getSecondsLeft() - 1);
    }

    public boolean phaseTimeExpired(StepByStepMatchState state) {
        return (isRoundPhase(state.effectivePhase()) || isStealPhase(state.effectivePhase()))
                && state.getSecondsLeft() <= 0;
    }

    public int secondsLeft(StepByStepMatchState state) {
        if (state.isFinished()) {
            return 0;
        }
        if ((isRoundPhase(state.effectivePhase()) || isStealPhase(state.effectivePhase()))
                && state.getVisibleStepCount() > 0) {
            if (isStealPhase(state.effectivePhase())) {
                return Math.min(STEAL_DURATION_MS / 1000, state.getSecondsLeft());
            }
            return Math.min(ROUND_DURATION_MS / 1000, state.getSecondsLeft());
        }
        return state.getSecondsLeft();
    }

    public int computedSecondsLeft(StepByStepMatchState state) {
        String phase = state.effectivePhase();
        long now = System.currentTimeMillis();
        if (StepByStepMatchState.PHASE_WAITING.equals(phase)) {
            return 0;
        }
        if (isStealPhase(phase)) {
            if (state.getStealStartedAt() <= 0) {
                return 0;
            }
            return Math.max(0, (int) ((STEAL_DURATION_MS - (now - state.getStealStartedAt())) / 1000));
        }
        if (isRoundPhase(phase)) {
            if (state.getRoundStartedAt() <= 0) {
                return 0;
            }
            return Math.max(0, (int) ((ROUND_DURATION_MS - (now - state.getRoundStartedAt())) / 1000));
        }
        return 0;
    }

    public int pointsForStep(int openedSteps) {
        return Math.max(8, 22 - (openedSteps * 2));
    }

    public boolean isMyTurn(StepByStepMatchState state, int myPlayer) {
        if (myPlayer == 0) {
            return false;
        }
        String phase = state.effectivePhase();
        if (isStealPhase(phase)) {
            return state.getStealPlayer() == myPlayer;
        }
        return isRoundPhase(phase)
                && state.getActivePlayer() == myPlayer;
    }

    public boolean waitingForServerTime(StepByStepMatchState state) {
        return false;
    }

    public boolean shouldStartSteal(StepByStepMatchState state) {
        return isRoundPhase(state.effectivePhase()) && phaseTimeExpired(state);
    }

    public boolean shouldFinishSteal(StepByStepMatchState state) {
        return isStealPhase(state.effectivePhase()) && phaseTimeExpired(state);
    }

    public String statusText(StepByStepMatchState state, int myPlayer) {
        if (state.isFinished()) {
            return "Kraj igre. Konacan rezultat je prikazan iznad.";
        }
        if (state.isForfeited(state.getPlayer1Id()) || state.isForfeited(state.getPlayer2Id())) {
            String status = state.getStatusMessage();
            return status == null || status.trim().isEmpty()
                    ? "Protivnik je napustio partiju."
                    : status;
        }
        String phase = state.effectivePhase();
        if (StepByStepMatchState.PHASE_WAITING.equals(phase)) {
            return myPlayer == 1 ? "Cekas da se drugi igrac pridruzi." : "Ceka se drugi igrac.";
        }
        if (isStealPhase(phase)) {
            return state.getStealPlayer() == myPlayer
                    ? "Protivnik nije pogodio. Unesi odgovor za 5 bodova."
                    : "Cekajte svoj red. Protivnik ima 10 sekundi za odgovor.";
        }
        if (state.getActivePlayer() == myPlayer) {
            return "Tvoja runda. Unesi konacni pojam pre isteka vremena.";
        }
        return "Cekajte svoj red. Drugi igrac trenutno odgovara.";
    }

    public boolean matches(String guess, String answer) {
        return normalized(guess).equals(normalized(answer));
    }

    public boolean isRoundPhase(String phase) {
        return StepByStepMatchState.PHASE_PLAYING.equals(phase)
                || StepByStepMatchState.PHASE_ROUND1.equals(phase)
                || StepByStepMatchState.PHASE_ROUND2.equals(phase);
    }

    public boolean isStealPhase(String phase) {
        return StepByStepMatchState.PHASE_STEAL.equals(phase)
                || StepByStepMatchState.PHASE_STEAL1.equals(phase)
                || StepByStepMatchState.PHASE_STEAL2.equals(phase);
    }

    private String normalized(String value) {
        String withoutMarks = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return withoutMarks.trim().toLowerCase(Locale.ROOT);
    }
}
