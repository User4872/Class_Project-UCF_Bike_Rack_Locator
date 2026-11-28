
package com.example.campus_bike_rack_locator;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class ProfileActivity extends AppCompatActivity {

    private final BikeRackRepository repo = new BikeRackRepository();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private final Map<String, String> rackNames = new HashMap<>();
    private boolean racksLoaded;
    private boolean started;
    private ListenerRegistration reportsListener;
    private String uid;

    private TextView tvName, tvUserSince, tvReportCount, tvEmpty;
    private LinearLayout container;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        uid = repo.currentUid();
        if (uid == null)
        {
            goToLogin();
            return;
        }

        setContentView(R.layout.activity_profile);
        tvName = findViewById(R.id.tvName);
        tvUserSince = findViewById(R.id.tvUserSince);
        tvReportCount = findViewById(R.id.tvReportCount);
        tvEmpty = findViewById(R.id.tvEmpty);
        container = findViewById(R.id.reportListContainer);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnLogout).setOnClickListener(v ->
        {
            repo.logout();
            goToLogin();
        });

        loadProfile();
    }

    @Override
    protected void onStart()
    {
        super.onStart();
        if (uid == null) return;
        started = true;

        if (racksLoaded)
        {
            startReports();
        }
        else
        {
            db.collection("racks").get().addOnCompleteListener(task ->
            {
                if (task.isSuccessful() && task.getResult() != null)
                {
                    for (QueryDocumentSnapshot doc : task.getResult())
                    {
                        rackNames.put(doc.getId(), doc.getString("name"));
                    }
                    racksLoaded = true;
                }
                if (started) startReports();
            });
        }
    }

    @Override
    protected void onStop()
    {
        super.onStop();
        started = false;
        if (reportsListener != null)
        {
            reportsListener.remove();
            reportsListener = null;
        }
    }

    private void goToLogin()
    {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void loadProfile()
    {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc ->
                {
                    if (!doc.exists())
                    {
                        tvName.setText("Name: (unavailable)");
                        return;
                    }
                    String first = doc.getString("firstName");
                    String last = doc.getString("lastName");
                    String full = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
                    tvName.setText("Name: " + (full.isEmpty() ? "(unavailable)" : full));

                    Timestamp joined = doc.getTimestamp("createdAt");
                    if (joined != null)
                    {
                        SimpleDateFormat fmt = new SimpleDateFormat("MMMM dd, yyyy", Locale.US);
                        tvUserSince.setText("User Since: " + fmt.format(joined.toDate()));
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this,
                        "Could not load your profile.", Toast.LENGTH_LONG).show());
    }

    private void startReports()
    {
        if (reportsListener != null) return;
        reportsListener = db.collection("reports").whereEqualTo("userId", uid)
                .addSnapshotListener((snap, error) ->
                {
                    if (error != null)
                    {
                        Toast.makeText(this, "Could not load your report history.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (snap == null) return;

                    List<DocumentSnapshot> docs = new ArrayList<>(snap.getDocuments());
                    Collections.sort(docs, (a, b) ->
                            Long.compare(millis(b), millis(a)));
                    showReports(docs);
                });
    }

    private long millis(DocumentSnapshot doc)
    {
        Timestamp t = doc.getTimestamp("createdAt",
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
        return t == null ? 0 : t.toDate().getTime();
    }

    private void showReports(List<DocumentSnapshot> docs)
    {
        container.removeAllViews();
        tvReportCount.setText("Reports submitted: " + docs.size());
        tvEmpty.setVisibility(docs.isEmpty() ? View.VISIBLE : View.GONE);

        SimpleDateFormat fmt = new SimpleDateFormat("MM-dd-yyyy   h:mm a", Locale.US);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (DocumentSnapshot doc : docs)
        {
            String rackId = doc.getString("rackId");
            String rackName = rackId == null ? null : rackNames.get(rackId);
            if (rackName == null) rackName = "Unknown rack";

            RackStatus status = RackStatus.fromKey(doc.getString("level"));
            Timestamp ts = doc.getTimestamp("createdAt",
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);

            View card = inflater.inflate(R.layout.item_report_card, container, false);
            ((TextView) card.findViewById(R.id.tvReportDateTime)).setText(ts == null ? "" : fmt
                    .format(new Date(ts.toDate().getTime())));
            ((TextView) card.findViewById(R.id.tvReportLocation)).setText("Report: " + rackName);
            ((TextView) card.findViewById(R.id.tvReportOccupancy)).setText("Occupancy: "
                    + (status == null ? "Unknown" : status.label));

            if (rackId != null)
            {
                final String id = rackId;
                final String name = rackName;
                card.setOnClickListener(v ->
                {
                    Intent intent = new Intent(this, OccupancyActivity.class);
                    intent.putExtra(OccupancyActivity.EXTRA_RACK_ID, id);
                    intent.putExtra(OccupancyActivity.EXTRA_RACK_NAME, name);
                    startActivity(intent);
                });
            }
            container.addView(card);
        }
    }
}