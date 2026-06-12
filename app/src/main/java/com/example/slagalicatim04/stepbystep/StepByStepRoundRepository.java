package com.example.slagalicatim04.stepbystep;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StepByStepRoundRepository {

    public interface Callback {
        void onSuccess(List<StepByStepRound> rounds);

        void onError(Exception error);
    }

    private static final String COLLECTION = "stepByStepRounds";

    private final FirebaseFirestore firestore;

    public StepByStepRoundRepository() {
        this.firestore = FirebaseFirestore.getInstance();
    }

    public void loadOrSeed(Callback callback) {
        firestore.collection(COLLECTION)
                .orderBy("index")
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        seedSampleRounds()
                                .addOnSuccessListener(ignored -> loadOrSeed(callback))
                                .addOnFailureListener(callback::onError);
                        return;
                    }
                    callback.onSuccess(mapRounds(snapshot.getDocuments()));
                })
                .addOnFailureListener(callback::onError);
    }

    private Task<Void> seedSampleRounds() {
        WriteBatch batch = firestore.batch();

        batch.set(firestore.collection(COLLECTION).document("round-1"), roundPayload(
                1,
                Arrays.asList(
                        "1. Hrvatski",
                        "2. Mladi",
                        "3. Pjevac",
                        "4. Vinkovci",
                        "5. Zvjezdice",
                        "6. Supertalent",
                        "7. Inicijali J. J."
                ),
                "Jakov Jozinovic"
        ));

        batch.set(firestore.collection(COLLECTION).document("round-2"), roundPayload(
                2,
                Arrays.asList(
                        "1. Srpski naucnik",
                        "2. Izumitelj",
                        "3. Naizmenicna struja",
                        "4. Colorado Springs",
                        "5. Golubovi",
                        "6. Jedinica za magnetnu indukciju",
                        "7. Inicijali N. T."
                ),
                "Nikola Tesla"
        ));

        return batch.commit();
    }

    private List<StepByStepRound> mapRounds(List<DocumentSnapshot> documents) {
        List<StepByStepRound> rounds = new ArrayList<>();
        for (DocumentSnapshot document : documents) {
            Long indexValue = document.getLong("index");
            String answer = document.getString("answer");
            List<String> steps = (List<String>) document.get("steps");
            if (indexValue == null || answer == null || steps == null) {
                continue;
            }
            rounds.add(new StepByStepRound(indexValue.intValue(), steps, answer));
        }
        rounds.sort((left, right) -> Integer.compare(left.getIndex(), right.getIndex()));
        return rounds;
    }

    private Object roundPayload(int index, List<String> steps, String answer) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("index", index);
        payload.put("steps", steps);
        payload.put("answer", answer);
        payload.put("updatedAt", FieldValue.serverTimestamp());
        return payload;
    }
}
