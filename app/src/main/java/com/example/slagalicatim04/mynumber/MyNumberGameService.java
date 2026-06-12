package com.example.slagalicatim04.mynumber;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class MyNumberGameService {
    public static final int ROUND_SECONDS = 60;
    public static final int REVEAL_SECONDS = 5;
    public static final String PHASE_ROUND1 = "myNumberRound1";
    public static final String PHASE_ROUND2 = "myNumberRound2";
    public static final String PHASE_FINISHED = "myNumberFinished";

    private final Random random = new Random();

    public int generateTarget() {
        return 100 + random.nextInt(900);
    }

    public List<Integer> generateNumbers() {
        List<Integer> numbers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            numbers.add(1 + random.nextInt(9));
        }
        List<Integer> medium = Arrays.asList(10, 15, 20);
        List<Integer> large = Arrays.asList(25, 50, 75, 100);
        numbers.add(medium.get(random.nextInt(medium.size())));
        numbers.add(large.get(random.nextInt(large.size())));
        return numbers;
    }

    public int activePlayerForRound(int round) {
        return round == 1 ? 1 : 2;
    }

    public Integer evaluate(String expression, List<Integer> allowedNumbers) {
        if (expression == null || expression.trim().isEmpty()) {
            return null;
        }
        ExpressionParser parser = new ExpressionParser(expression);
        int value = parser.parseExpression();
        if (!parser.isAtEnd()) {
            return null;
        }
        return usesAllowedNumbers(parser.getUsedNumbers(), allowedNumbers) ? value : null;
    }

    public RoundScore scoreRound(int target, int activePlayer, Integer p1Result, Integer p2Result) {
        boolean p1Exact = p1Result != null && p1Result == target;
        boolean p2Exact = p2Result != null && p2Result == target;
        if (p1Exact && !p2Exact) return new RoundScore(10, 0, "Igrac 1 je pogodio tacan broj.");
        if (p2Exact && !p1Exact) return new RoundScore(0, 10, "Igrac 2 je pogodio tacan broj.");
        if (p1Exact) {
            return activePlayer == 1
                    ? new RoundScore(10, 0, "Oba igraca imaju tacan broj. Prednost ima igrac cija je runda.")
                    : new RoundScore(0, 10, "Oba igraca imaju tacan broj. Prednost ima igrac cija je runda.");
        }

        int p1Diff = p1Result == null ? Integer.MAX_VALUE : Math.abs(target - p1Result);
        int p2Diff = p2Result == null ? Integer.MAX_VALUE : Math.abs(target - p2Result);
        if (p1Diff == Integer.MAX_VALUE && p2Diff == Integer.MAX_VALUE) {
            return new RoundScore(0, 0, "Nijedan igrac nije poslao resenje.");
        }
        if (p1Diff < p2Diff) return new RoundScore(5, 0, "Igrac 1 je blizi trazenom broju.");
        if (p2Diff < p1Diff) return new RoundScore(0, 5, "Igrac 2 je blizi trazenom broju.");
        return activePlayer == 1
                ? new RoundScore(5, 0, "Rezultati su jednako blizu. Prednost ima igrac cija je runda.")
                : new RoundScore(0, 5, "Rezultati su jednako blizu. Prednost ima igrac cija je runda.");
    }

    private boolean usesAllowedNumbers(List<Integer> usedNumbers, List<Integer> allowedNumbers) {
        List<Integer> remaining = new ArrayList<>(allowedNumbers);
        for (Integer used : usedNumbers) {
            if (!remaining.remove(used)) {
                return false;
            }
        }
        return true;
    }

    public static class RoundScore {
        public final int p1Points;
        public final int p2Points;
        public final String message;

        RoundScore(int p1Points, int p2Points, String message) {
            this.p1Points = p1Points;
            this.p2Points = p2Points;
            this.message = message;
        }
    }

    private static class ExpressionParser {
        private final String input;
        private final List<Integer> usedNumbers = new ArrayList<>();
        private int pos;

        ExpressionParser(String input) {
            this.input = input.replace(" ", "");
        }

        int parseExpression() {
            int value = parseTerm();
            while (pos < input.length()) {
                char op = input.charAt(pos);
                if (op != '+' && op != '-') break;
                pos++;
                int rhs = parseTerm();
                value = op == '+' ? value + rhs : value - rhs;
            }
            return value;
        }

        int parseTerm() {
            int value = parseFactor();
            while (pos < input.length()) {
                char op = input.charAt(pos);
                if (op != '*' && op != '/') break;
                pos++;
                int rhs = parseFactor();
                if (op == '/') {
                    if (rhs == 0 || value % rhs != 0) throw new IllegalArgumentException();
                    value /= rhs;
                } else {
                    value *= rhs;
                }
            }
            return value;
        }

        int parseFactor() {
            if (pos >= input.length()) throw new IllegalArgumentException();
            if (input.charAt(pos) == '(') {
                pos++;
                int value = parseExpression();
                if (pos >= input.length() || input.charAt(pos) != ')') throw new IllegalArgumentException();
                pos++;
                return value;
            }
            int start = pos;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++;
            if (start == pos) throw new IllegalArgumentException();
            int value = Integer.parseInt(input.substring(start, pos));
            usedNumbers.add(value);
            return value;
        }

        boolean isAtEnd() {
            return pos == input.length();
        }

        List<Integer> getUsedNumbers() {
            return usedNumbers;
        }
    }
}
