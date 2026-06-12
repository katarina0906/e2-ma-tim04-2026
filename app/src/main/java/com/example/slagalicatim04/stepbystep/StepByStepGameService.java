package com.example.slagalicatim04.stepbystep;

import java.text.Normalizer;
import java.util.Locale;

public class StepByStepGameService {
    public static final int ROUND_DURATION_MS = 70_000;
    public static final int STEAL_DURATION_MS = 10_000;

    public int openedSteps(StepByStepMatchState state) {
        String phase = state.effectivePhase();
        long roundStartedAt = state.getRoundStartedAt();
        if (StepByStepMatchState.PHASE_WAITING.equals(phase) || roundStartedAt <= 0) {
            return 0;
        }
        if (!StepByStepMatchState.PHASE_PLAYING.equals(phase)
                && !StepByStepMatchState.PHASE_STEAL.equals(phase)) {
            return 7;
        }
        int elapsed = (int) Math.max(0, System.currentTimeMillis() - roundStartedAt);
        return Math.max(1, Math.min(7, 1 + (elapsed / 10_000)));
    }

    public int secondsLeft(StepByStepMatchState state) {
        String phase = state.effectivePhase();
        long now = System.currentTimeMillis();
        if (StepByStepMatchState.PHASE_WAITING.equals(phase) || state.getRoundStartedAt() <= 0) {
            return 0;
        }
        if (StepByStepMatchState.PHASE_STEAL.equals(phase)) {
            return Math.max(0, (int) ((STEAL_DURATION_MS - (now - state.getStealStartedAt())) / 1000));
        }
        if (StepByStepMatchState.PHASE_PLAYING.equals(phase)) {
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
        if (StepByStepMatchState.PHASE_STEAL.equals(phase)) {
            return state.getStealPlayer() == myPlayer;
        }
        return StepByStepMatchState.PHASE_PLAYING.equals(phase)
                && state.getActivePlayer() == myPlayer;
    }

    public boolean waitingForServerTime(StepByStepMatchState state) {
        String phase = state.effectivePhase();
        return (StepByStepMatchState.PHASE_PLAYING.equals(phase) && state.getRoundStartedAt() <= 0)
                || (StepByStepMatchState.PHASE_STEAL.equals(phase) && state.getStealStartedAt() <= 0);
    }

    public boolean shouldStartSteal(StepByStepMatchState state) {
        return StepByStepMatchState.PHASE_PLAYING.equals(state.effectivePhase())
                && state.getRoundStartedAt() > 0
                && System.currentTimeMillis() - state.getRoundStartedAt() >= ROUND_DURATION_MS;
    }

    public boolean shouldFinishSteal(StepByStepMatchState state) {
        return StepByStepMatchState.PHASE_STEAL.equals(state.effectivePhase())
                && state.getStealStartedAt() > 0
                && System.currentTimeMillis() - state.getStealStartedAt() >= STEAL_DURATION_MS;
    }

    public String statusText(StepByStepMatchState state, int myPlayer) {
        if (state.isFinished()) {
            return "Kraj igre. Konacan rezultat je prikazan iznad.";
        }
        String phase = state.effectivePhase();
        if (StepByStepMatchState.PHASE_WAITING.equals(phase)) {
            return myPlayer == 1 ? "Cekas da se drugi igrac pridruzi." : "Ceka se drugi igrac.";
        }
        if (StepByStepMatchState.PHASE_STEAL.equals(phase)) {
            return state.getStealPlayer() == myPlayer
                    ? "Tvoja sansa za 5 bodova. Imas 10 sekundi."
                    : "Protivnik ima sansu za 5 bodova.";
        }
        if (state.getActivePlayer() == myPlayer) {
            return "Tvoja runda. Pogodi pojam pre isteka vremena.";
        }
        return state.getStatusMessage().isEmpty()
                ? "Cekas potez drugog igraca."
                : state.getStatusMessage();
    }

    public boolean matches(String guess, String answer) {
        return normalized(guess).equals(normalized(answer));
    }

    private String normalized(String value) {
        String withoutMarks = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return withoutMarks.trim().toLowerCase(Locale.ROOT);
    }
}
