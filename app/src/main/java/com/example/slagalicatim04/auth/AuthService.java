package com.example.slagalicatim04.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Patterns;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class AuthService {
    private static final String PREFS_NAME = "slagalica_auth";
    private static final String KEY_CURRENT_USER_ID = "current_user_id";
    private static final String KEY_CURRENT_USER_EMAIL = "current_user_email";
    private static final String KEY_CURRENT_USERNAME = "current_username";
    private static final String KEY_CURRENT_REGION = "current_region";
    private static final String KEY_CURRENT_AVATAR_URL = "current_avatar_url";

    private static AuthService instance;

    private final SharedPreferences preferences;
    private final FirebaseAuth firebaseAuth;
    private final FirebaseFirestore firestore;
    private final FirebaseStorage storage;

    private AuthService(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (FirebaseApp.getApps(context.getApplicationContext()).isEmpty()) {
            firebaseAuth = null;
            firestore = null;
            storage = null;
        } else {
            firebaseAuth = FirebaseAuth.getInstance();
            firestore = FirebaseFirestore.getInstance();
            storage = FirebaseStorage.getInstance();
        }
    }

    public static synchronized AuthService getInstance(Context context) {
        if (instance == null) {
            instance = new AuthService(context);
        }
        return instance;
    }

    public AuthResult<AuthUser> register(String email, String username, String region,
                                         String password, String repeatedPassword) {
        if (!isFirebaseConfigured()) {
            return firebaseNotConfigured();
        }
        String normalizedEmail = normalize(email);
        String normalizedUsername = normalize(username);
        String cleanedRegion = clean(region);

        String validationError = validateRegistration(normalizedEmail, normalizedUsername, cleanedRegion,
                password, repeatedPassword);
        if (validationError != null) {
            return AuthResult.error(validationError);
        }

        try {
            DocumentSnapshot usernameDoc = Tasks.await(firestore.collection("usernames")
                    .document(normalizedUsername)
                    .get());
            if (usernameDoc.exists()) {
                return AuthResult.error("Korisnicko ime je zauzeto.");
            }

            FirebaseUser firebaseUser = Tasks.await(firebaseAuth
                    .createUserWithEmailAndPassword(normalizedEmail, password))
                    .getUser();
            if (firebaseUser == null) {
                return AuthResult.error("Registracija nije uspela.");
            }

            AuthUser authUser = new AuthUser(firebaseUser.getUid(), normalizedEmail,
                    normalizedUsername, cleanedRegion, "", "", false, "");
            saveProfile(authUser);
            Tasks.await(firebaseUser.sendEmailVerification());

            return AuthResult.success(authUser,
                    "Registracija je sacuvana. Verifikacioni link je poslat na mejl.");
        } catch (ExecutionException e) {
            return AuthResult.error(firebaseMessage(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AuthResult.error("Operacija je prekinuta.");
        }
    }

    public AuthResult<AuthUser> login(String identifier, String password) {
        if (!isFirebaseConfigured()) {
            return firebaseNotConfigured();
        }
        String cleanedIdentifier = normalize(identifier);
        try {
            String email = resolveEmail(cleanedIdentifier);
            FirebaseUser firebaseUser = Tasks.await(firebaseAuth
                    .signInWithEmailAndPassword(email, password))
                    .getUser();
            if (firebaseUser == null) {
                return AuthResult.error("Pogresan mejl/korisnicko ime ili lozinka.");
            }

            Tasks.await(firebaseUser.reload());
            firebaseUser = firebaseAuth.getCurrentUser();
            if (firebaseUser == null || !firebaseUser.isEmailVerified()) {
                firebaseAuth.signOut();
                return AuthResult.error("Prvo potvrdi registraciju klikom na link poslat na mejl.");
            }

            AuthUser authUser = loadProfile(firebaseUser);
            saveCurrentUser(authUser);
            return AuthResult.success(authUser, "Uspesna prijava.");
        } catch (ExecutionException e) {
            return AuthResult.error(firebaseMessage(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AuthResult.error("Operacija je prekinuta.");
        }
    }

    public AuthResult<AuthUser> verifyEmail(String tokenOrLink) {
        return AuthResult.error("Potvrda naloga se radi klikom na Firebase link u mejlu.");
    }

    public AuthResult<AuthUser> verifyCode(String identifier, String code) {
        return AuthResult.error("Potvrda naloga se radi klikom na Firebase link u mejlu.");
    }

    public AuthResult<AuthUser> resendVerification(String identifier) {
        if (!isFirebaseConfigured()) {
            return firebaseNotConfigured();
        }
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            return AuthResult.error("Prijavi se na nepotvrdjeni nalog pa posalji link ponovo.");
        }
        try {
            Tasks.await(currentUser.reload());
            currentUser = firebaseAuth.getCurrentUser();
            if (currentUser == null) {
                return AuthResult.error("Nalog nije pronadjen.");
            }
            if (currentUser.isEmailVerified()) {
                return AuthResult.error("Mejl je vec potvrdjen.");
            }
            Tasks.await(currentUser.sendEmailVerification());
            return AuthResult.success(firebaseUserOnly(currentUser), "Novi verifikacioni link je poslat.");
        } catch (ExecutionException e) {
            return AuthResult.error(firebaseMessage(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AuthResult.error("Operacija je prekinuta.");
        }
    }

    public AuthResult<AuthUser> changePassword(String identifier, String oldPassword,
                                               String newPassword, String repeatedNewPassword) {
        if (!isFirebaseConfigured()) {
            return firebaseNotConfigured();
        }
        String passwordError = validatePassword(newPassword, repeatedNewPassword);
        if (passwordError != null) {
            return AuthResult.error(passwordError);
        }

        try {
            String email = resolveEmail(normalize(identifier));
            FirebaseUser firebaseUser = Tasks.await(firebaseAuth
                    .signInWithEmailAndPassword(email, oldPassword))
                    .getUser();
            if (firebaseUser == null) {
                return AuthResult.error("Pogresan nalog ili stara lozinka.");
            }

            AuthCredential credential = EmailAuthProvider.getCredential(email, oldPassword);
            Tasks.await(firebaseUser.reauthenticate(credential));
            Tasks.await(firebaseUser.updatePassword(newPassword));
            return AuthResult.success(firebaseUserOnly(firebaseUser), "Lozinka je promenjena.");
        } catch (ExecutionException e) {
            return AuthResult.error(firebaseMessage(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AuthResult.error("Operacija je prekinuta.");
        }
    }

    public AuthUser getCurrentUser() {
        if (!isFirebaseConfigured()) {
            return null;
        }
        FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
        String currentUserId = preferences.getString(KEY_CURRENT_USER_ID, "");
        if (firebaseUser == null || currentUserId.isEmpty()
                || !firebaseUser.getUid().equals(currentUserId)) {
            clearCurrentUser();
            return null;
        }
        return new AuthUser(
                currentUserId,
                preferences.getString(KEY_CURRENT_USER_EMAIL, ""),
                preferences.getString(KEY_CURRENT_USERNAME, ""),
                preferences.getString(KEY_CURRENT_REGION, ""),
                "",
                "",
                true,
                "",
                preferences.getString(KEY_CURRENT_AVATAR_URL, "")
        );
    }

    public AuthResult<AuthUser> refreshCurrentUser() {
        if (!isFirebaseConfigured()) {
            return firebaseNotConfigured();
        }
        FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
        if (firebaseUser == null) {
            clearCurrentUser();
            return AuthResult.error("Korisnik nije prijavljen.");
        }
        try {
            AuthUser authUser = loadProfile(firebaseUser);
            saveCurrentUser(authUser);
            return AuthResult.success(authUser, "Profil je ucitan.");
        } catch (ExecutionException e) {
            return AuthResult.error(firebaseMessage(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AuthResult.error("Ucitavanje profila je prekinuto.");
        }
    }

    public void logout() {
        if (firebaseAuth != null) {
            firebaseAuth.signOut();
        }
        clearCurrentUser();
    }

    public AuthResult<AuthUser> updateAvatar(Uri imageUri) {
        if (!isFirebaseConfigured() || storage == null) {
            return firebaseNotConfigured();
        }
        FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
        if (firebaseUser == null) {
            return AuthResult.error("Korisnik nije prijavljen.");
        }
        try {
            StorageReference avatarRef = storage.getReference()
                    .child("profile_avatars/" + firebaseUser.getUid() + ".jpg");
            Tasks.await(avatarRef.putFile(imageUri));
            String avatarUrl = Tasks.await(avatarRef.getDownloadUrl()).toString();
            Tasks.await(firestore.collection("users").document(firebaseUser.getUid())
                    .update("avatarUrl", avatarUrl));
            AuthUser user = loadProfile(firebaseUser);
            saveCurrentUser(user);
            return AuthResult.success(user, "Profilna slika je sacuvana.");
        } catch (ExecutionException e) {
            return AuthResult.error(firebaseMessage(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AuthResult.error("Cuvanje slike je prekinuto.");
        }
    }

    private void saveProfile(AuthUser authUser) throws ExecutionException, InterruptedException {
        Map<String, Object> userData = new HashMap<>();
        userData.put("email", authUser.getEmail());
        userData.put("username", authUser.getUsername());
        userData.put("region", authUser.getRegion());
        userData.put("avatarUrl", authUser.getAvatarUrl());

        Map<String, Object> usernameData = new HashMap<>();
        usernameData.put("email", authUser.getEmail());
        usernameData.put("uid", authUser.getId());

        Tasks.await(firestore.collection("users").document(authUser.getId()).set(userData));
        Tasks.await(firestore.collection("usernames").document(authUser.getUsername()).set(usernameData));
    }

    private boolean isFirebaseConfigured() {
        return firebaseAuth != null && firestore != null;
    }

    private AuthResult<AuthUser> firebaseNotConfigured() {
        return AuthResult.error("Firebase nije konfigurisan. Dodaj app/google-services.json i ukljuci Email/Password auth.");
    }

    private AuthUser loadProfile(FirebaseUser firebaseUser) throws ExecutionException, InterruptedException {
        DocumentSnapshot userDoc = Tasks.await(firestore.collection("users")
                .document(firebaseUser.getUid())
                .get());
        String email = firebaseUser.getEmail() == null ? "" : firebaseUser.getEmail();
        String username = userDoc.getString("username");
        String region = userDoc.getString("region");
        String avatarUrl = userDoc.getString("avatarUrl");
        return new AuthUser(firebaseUser.getUid(), email,
                username == null ? "" : username,
                region == null ? "" : region,
                "", "", firebaseUser.isEmailVerified(), "",
                avatarUrl == null ? "" : avatarUrl);
    }

    private AuthUser firebaseUserOnly(FirebaseUser firebaseUser) {
        String email = firebaseUser.getEmail() == null ? "" : firebaseUser.getEmail();
        return new AuthUser(firebaseUser.getUid(), email, "", "", "", "",
                firebaseUser.isEmailVerified(), "");
    }

    private String resolveEmail(String identifier) throws ExecutionException, InterruptedException {
        if (Patterns.EMAIL_ADDRESS.matcher(identifier).matches()) {
            return identifier;
        }
        DocumentSnapshot usernameDoc = Tasks.await(firestore.collection("usernames")
                .document(identifier)
                .get());
        String email = usernameDoc.getString("email");
        if (email == null || email.isEmpty()) {
            throw new ExecutionException(new IllegalArgumentException("Nalog nije pronadjen."));
        }
        return email;
    }

    private void saveCurrentUser(AuthUser user) {
        preferences.edit()
                .putString(KEY_CURRENT_USER_ID, user.getId())
                .putString(KEY_CURRENT_USER_EMAIL, user.getEmail())
                .putString(KEY_CURRENT_USERNAME, user.getUsername())
                .putString(KEY_CURRENT_REGION, user.getRegion())
                .putString(KEY_CURRENT_AVATAR_URL, user.getAvatarUrl())
                .apply();
    }

    private void clearCurrentUser() {
        preferences.edit()
                .remove(KEY_CURRENT_USER_ID)
                .remove(KEY_CURRENT_USER_EMAIL)
                .remove(KEY_CURRENT_USERNAME)
                .remove(KEY_CURRENT_REGION)
                .remove(KEY_CURRENT_AVATAR_URL)
                .apply();
    }

    private String validateRegistration(String email, String username, String region,
                                        String password, String repeatedPassword) {
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return "Unesi ispravnu email adresu.";
        }
        if (username.length() < 3) {
            return "Korisnicko ime mora imati najmanje 3 karaktera.";
        }
        if (TextUtils.isEmpty(region)) {
            return "Region je obavezan.";
        }
        return validatePassword(password, repeatedPassword);
    }

    private String validatePassword(String password, String repeatedPassword) {
        if (password == null || password.length() < 8) {
            return "Lozinka mora imati najmanje 8 karaktera.";
        }
        if (!password.equals(repeatedPassword)) {
            return "Lozinke se ne poklapaju.";
        }
        return null;
    }

    private String firebaseMessage(ExecutionException e) {
        Throwable cause = e.getCause();
        String message = cause == null ? e.getMessage() : cause.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "Firebase operacija nije uspela.";
        }
        return message;
    }

    private String normalize(String value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
