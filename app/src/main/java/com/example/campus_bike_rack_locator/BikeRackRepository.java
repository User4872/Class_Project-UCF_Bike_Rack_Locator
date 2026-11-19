package com.example.campus_bike_rack_locator;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.WriteBatch;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Talks straight to Firebase. Each public method is one "endpoint":
 *   register  -> GP-19
 *   login     -> GP-20
 *   submitReport -> GP-22
 *
 * Callbacks fire on the main thread, so it is safe to show a Toast/Snackbar/Dialog
 * or start an Activity directly inside onSuccess/onError.
 *
 * Usage from an Activity:
 *
 *   BikeRackRepository repo = new BikeRackRepository();
 *   repo.login(usernameField.getText().toString(), passwordField.getText().toString(),
 *       new BikeRackRepository.Callback() {
 *           public void onSuccess() { startActivity(new Intent(LoginActivity.this, MapActivity.class)); }
 *           public void onError(String message) {
 *               Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show();
 *           }
 *       });
 *
 * Assumptions (change if they are wrong):
 *   - Racks live in the "racks" collection and the document ID is the rack ID you pass in.
 *   - Login uses a username; Firebase Auth needs an email, so the username is turned
 *     into a fake email (username + EMAIL_DOMAIN). To use real emails instead, change
 *     toEmail() and add an email field to the sign-up screen.
 */
public class BikeRackRepository {

    public interface Callback {
        void onSuccess();
        void onError(String message);
    }

    // Must match validLevel() in the Firestore rules exactly.
    public static final List<String> LEVELS =
            Arrays.asList("empty", "low", "half", "nearly_full", "full");

    private static final String EMAIL_DOMAIN = "@bikerack.example";

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private static String cleanUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.US);
    }

    private static String toEmail(String username) {
        return cleanUsername(username) + EMAIL_DOMAIN;
    }

    // ---------- Session helpers (Auth keeps the session across app restarts) ----------

    public boolean isLoggedIn() {
        return auth.getCurrentUser() != null;
    }

    public String currentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? null : user.getUid();
    }

    public void logout() {
        auth.signOut();
    }

    // ---------- GP-19: Register ----------

    public void register(String firstName, String lastName, String username,
                         String password, Callback cb) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        String user = cleanUsername(username);

        if (first.isEmpty() || last.isEmpty()) {
            cb.onError("Please enter your first and last name.");
            return;
        }
        if (!user.matches("[a-z0-9._]{3,20}")) {
            cb.onError("Username must be 3-20 characters: letters, numbers, dots or underscores.");
            return;
        }
        if (password == null || password.length() < 6) {
            cb.onError("Password must be at least 6 characters.");
            return;
        }

        auth.createUserWithEmailAndPassword(toEmail(user), password)
                .addOnSuccessListener(result -> {
                    // Creating the account signs the user in, so the rules (signedIn) pass.
                    String uid = result.getUser().getUid();

                    // Rules allow exactly these four keys, and createdAt must be request.time.
                    Map<String, Object> profile = new HashMap<>();
                    profile.put("firstName", first);
                    profile.put("lastName", last);
                    profile.put("username", user);
                    profile.put("createdAt", FieldValue.serverTimestamp());

                    // Document ID must equal the Auth uid (rules: request.auth.uid == userId).
                    db.collection("users").document(uid).set(profile)
                            .addOnSuccessListener(v -> cb.onSuccess())
                            .addOnFailureListener(e -> cb.onError(
                                    "Account created, but your profile could not be saved. "
                                            + "Please log in and try again."));
                })
                .addOnFailureListener(e -> {
                    if (e instanceof FirebaseAuthUserCollisionException) {
                        cb.onError("That username is already taken.");
                    } else if (e instanceof FirebaseNetworkException) {
                        cb.onError("No connection. Check your internet and try again.");
                    } else {
                        cb.onError("Could not create your account. Please try again.");
                    }
                });
    }

    // ---------- GP-20: Login ----------

    public void login(String username, String password, Callback cb) {
        String user = cleanUsername(username);
        if (user.isEmpty() || password == null || password.isEmpty()) {
            cb.onError("Enter your username and password.");
            return;
        }

        auth.signInWithEmailAndPassword(toEmail(user), password)
                .addOnSuccessListener(result -> cb.onSuccess())
                .addOnFailureListener(e -> {
                    if (e instanceof FirebaseAuthInvalidUserException
                            || e instanceof FirebaseAuthInvalidCredentialsException) {
                        cb.onError("Wrong username or password.");
                    } else if (e instanceof FirebaseNetworkException) {
                        cb.onError("No connection. Check your internet and try again.");
                    } else {
                        cb.onError("Could not log in. Please try again.");
                    }
                });
    }

    // ---------- GP-22: Occupancy report ----------

    /**
     * Saves one report and updates the rack's current status in a single atomic batch.
     *
     * @param rackId the Firestore document ID of the rack in "racks"
     * @param level  one of LEVELS: empty, low, half, nearly_full, full
     */
    public void submitReport(String rackId, String level, Callback cb) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            cb.onError("Please log in to submit a report.");
            return;
        }
        if (rackId == null || rackId.trim().isEmpty()) {
            cb.onError("Pick a bike rack first.");
            return;
        }
        if (level == null || !LEVELS.contains(level)) {
            cb.onError("Choose how full the rack is.");
            return;
        }

        WriteBatch batch = db.batch();

        // Rules allow exactly these four keys; createdAt must be request.time.
        DocumentReference reportRef = db.collection("reports").document();
        Map<String, Object> report = new HashMap<>();
        report.put("rackId", rackId);
        report.put("userId", user.getUid());
        report.put("level", level);
        report.put("createdAt", FieldValue.serverTimestamp());
        batch.set(reportRef, report);

        // Rules only let a rack change these two fields.
        Map<String, Object> rackUpdate = new HashMap<>();
        rackUpdate.put("currentStatus", level);
        rackUpdate.put("lastReportAt", FieldValue.serverTimestamp());
        batch.update(db.collection("racks").document(rackId), rackUpdate);

        batch.commit()
                .addOnSuccessListener(v -> cb.onSuccess())
                .addOnFailureListener(e -> {
                    if (e instanceof FirebaseNetworkException) {
                        cb.onError("No connection. Check your internet and try again.");
                    } else if (e instanceof FirebaseFirestoreException
                            && ((FirebaseFirestoreException) e).getCode()
                            == FirebaseFirestoreException.Code.NOT_FOUND) {
                        cb.onError("That rack could not be found.");
                    } else if (e instanceof FirebaseFirestoreException
                            && ((FirebaseFirestoreException) e).getCode()
                            == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        cb.onError("You don't have permission to submit that report.");
                    } else {
                        cb.onError("Could not submit your report. Please try again.");
                    }
                });
    }
}