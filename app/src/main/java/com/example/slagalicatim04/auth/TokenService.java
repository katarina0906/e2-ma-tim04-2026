package com.example.slagalicatim04.auth;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Transaction;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;

public class TokenService {
    public static final int INITIAL_TOKENS = 5;
    public static final int DAILY_TOKENS = 5;

    private static final String USERS_COLLECTION = "users";
    private static final String FIELD_TOKENS = "tokens";
    private static final String FIELD_LAST_TOKEN_GRANT_DATE = "lastTokenGrantDate";
    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);

    static {
        DATE_FORMAT.setTimeZone(TimeZone.getDefault());
    }

    private final FirebaseFirestore firestore;

    public TokenService(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    public int ensureDailyTokens(String userId) throws ExecutionException, InterruptedException {
        return Tasks.await(firestore.runTransaction((Transaction.Function<Integer>) transaction ->
                ensureDailyTokens(transaction, userId)));
    }

    public int ensureDailyTokens(Transaction transaction, String userId)
            throws FirebaseFirestoreException {
        DocumentReference userRef = firestore.collection(USERS_COLLECTION).document(userId);
        DocumentSnapshot snapshot = transaction.get(userRef);
        return ensureDailyTokens(transaction, userRef, snapshot);
    }

    public void consumeSingleToken(Transaction transaction, String userId)
            throws FirebaseFirestoreException {
        DocumentReference userRef = firestore.collection(USERS_COLLECTION).document(userId);
        DocumentSnapshot snapshot = transaction.get(userRef);
        int availableTokens = ensureDailyTokens(transaction, userRef, snapshot);
        if (availableTokens < 1) {
            throw new IllegalStateException("Nemate dovoljno tokena za novu partiju.");
        }
        transaction.update(userRef, FIELD_TOKENS, availableTokens - 1);
    }

    private int ensureDailyTokens(Transaction transaction, DocumentReference userRef,
                                  DocumentSnapshot snapshot) {
        String today = DATE_FORMAT.format(new Date());
        Long storedTokens = snapshot.getLong(FIELD_TOKENS);
        String lastGrantDate = snapshot.getString(FIELD_LAST_TOKEN_GRANT_DATE);
        int tokens = storedTokens == null ? INITIAL_TOKENS : Math.max(0, storedTokens.intValue());

        if (lastGrantDate == null || lastGrantDate.trim().isEmpty()) {
            transaction.update(userRef,
                    FIELD_TOKENS, tokens,
                    FIELD_LAST_TOKEN_GRANT_DATE, today);
            return tokens;
        }

        long daysBetween = daysBetween(lastGrantDate, today);
        if (daysBetween > 0) {
            tokens += (int) daysBetween * DAILY_TOKENS;
            transaction.update(userRef,
                    FIELD_TOKENS, tokens,
                    FIELD_LAST_TOKEN_GRANT_DATE, today);
        }
        return tokens;
    }

    private long daysBetween(String from, String to) {
        try {
            Date fromDate = DATE_FORMAT.parse(from);
            Date toDate = DATE_FORMAT.parse(to);
            if (fromDate == null || toDate == null) {
                return 0;
            }
            long diffMs = toDate.getTime() - fromDate.getTime();
            return Math.max(0, diffMs / (24L * 60L * 60L * 1000L));
        } catch (ParseException e) {
            return 0;
        }
    }
}
