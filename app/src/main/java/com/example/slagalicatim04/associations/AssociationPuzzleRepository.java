package com.example.slagalicatim04.associations;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AssociationPuzzleRepository {
    public interface Callback {
        void onSuccess(List<AssociationPuzzle> puzzles);
        void onError(Exception error);
    }

    private static final String COLLECTION = "associationPuzzles";
    private final CollectionReference puzzles =
            FirebaseFirestore.getInstance().collection(COLLECTION);

    public void loadOrSeed(Callback callback) {
        puzzles.get().addOnSuccessListener(snapshot -> {
            List<AssociationPuzzle> loaded = new ArrayList<>();
            snapshot.getDocuments().forEach(document -> {
                AssociationPuzzle puzzle = new AssociationPuzzle(document);
                if (puzzle.isValid()) {
                    loaded.add(puzzle);
                }
            });
            loaded.sort(Comparator.comparing(AssociationPuzzle::getId));
            if (loaded.size() >= AssociationGameService.ROUND_COUNT) {
                callback.onSuccess(loaded);
                return;
            }
            seed(callback);
        }).addOnFailureListener(callback::onError);
    }

    private void seed(Callback callback) {
        List<AssociationPuzzle> defaults = defaultPuzzles();
        WriteBatch batch = puzzles.getFirestore().batch();
        for (AssociationPuzzle puzzle : defaults) {
            Map<String, Object> data = new HashMap<>();
            data.put("clues", puzzle.getClues());
            data.put("columnAnswers", puzzle.getColumnAnswers());
            data.put("finalAnswer", puzzle.getFinalAnswer());
            batch.set(puzzles.document(puzzle.getId()), data);
        }
        batch.commit()
                .addOnSuccessListener(ignored -> callback.onSuccess(defaults))
                .addOnFailureListener(callback::onError);
    }

    private List<AssociationPuzzle> defaultPuzzles() {
        AssociationPuzzle first = new AssociationPuzzle(
                "association-1",
                Arrays.asList(
                        "Jabuka", "Kruska", "Banana", "Narandza",
                        "Krompir", "Luk", "Sargarepa", "Kupus",
                        "Psenica", "Jecam", "Raz", "Ovas",
                        "Sir", "Jogurt", "Kajmak", "Pavlaka"
                ),
                Arrays.asList("VOCE", "POVRCE", "ZITO", "MLECNO"),
                "HRANA"
        );
        AssociationPuzzle second = new AssociationPuzzle(
                "association-2",
                Arrays.asList(
                        "Crvena", "Plava", "Zelena", "Zuta",
                        "Trougao", "Kvadrat", "Krug", "Elipsa",
                        "Violina", "Gitara", "Klavir", "Bubanj",
                        "Drama", "Komedija", "Tragedija", "Mjuzikl"
                ),
                Arrays.asList("BOJE", "OBLICI", "INSTRUMENTI", "ZANROVI"),
                "UMETNOST"
        );
        return Arrays.asList(first, second);
    }
}
