package com.example.campus_bike_rack_locator;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Date;

public class OccupancyActivity extends AppCompatActivity {

    public static final String EXTRA_RACK_ID = "rack_id";
    public static final String EXTRA_RACK_NAME = "rack_name";

    private static final String STATE_EDITING = "editing";
    private static final String STATE_PENDING = "pending";

    private final BikeRackRepository repo = new BikeRackRepository();

    private String rackId;
    private RackStatus currentStatus;
    private Date lastReport;
    private boolean editing;
    private boolean saving;
    private RackStatus pending = RackStatus.HALF;
    private ListenerRegistration listener;
    private ListenerRegistration ratingsListener;
    private int recommendedCount, notRecommendedCount;
    private String myRating;
    private boolean ratingBusy;
    private TextView tvRackName, tvHeading, tvLevel, tvLastReported;
    private ProgressBar bar;
    private View viewPanel, updatePanel, ratingPanel;
    private TextView tvRatingSummary;
    private Button btnSave, btnDecrease, btnIncrease, btnRecommend, btnNotRecommend;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_occupancy);

        rackId = getIntent().getStringExtra(EXTRA_RACK_ID);
        if (rackId == null)
        {
            finish();
            return;
        }

        if (savedInstanceState != null)
        {
            editing = savedInstanceState.getBoolean(STATE_EDITING, false);
            int saved = savedInstanceState.getInt(STATE_PENDING, RackStatus.HALF.ordinal());
            pending = RackStatus.values()[saved];
        }

        tvRackName = findViewById(R.id.tvRackName);
        tvHeading = findViewById(R.id.tvOccupancyHeading);
        tvLevel = findViewById(R.id.tvOccupancyLevel);
        tvLastReported = findViewById(R.id.tvLastReported);
        bar = findViewById(R.id.occupancyBar);
        viewPanel = findViewById(R.id.viewPanel);
        updatePanel = findViewById(R.id.updatePanel);
        btnSave = findViewById(R.id.btnSave);
        btnDecrease = findViewById(R.id.btnDecrease);
        btnIncrease = findViewById(R.id.btnIncrease);
        ratingPanel = findViewById(R.id.ratingPanel);
        tvRatingSummary = findViewById(R.id.tvRatingSummary);
        btnRecommend = findViewById(R.id.btnRecommend);
        btnNotRecommend = findViewById(R.id.btnNotRecommend);
        btnRecommend.setOnClickListener(v -> rate(BikeRackRepository.QUALITY_RECOMMENDED));
        btnNotRecommend.setOnClickListener(v -> rate(BikeRackRepository.QUALITY_NOT_RECOMMENDED));

        String name = getIntent().getStringExtra(EXTRA_RACK_NAME);
        if (name != null) tvRackName.setText(name);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnProfile).setOnClickListener(v ->
                Toast.makeText(this, "Profile screen coming soon", Toast.LENGTH_SHORT).show());

        findViewById(R.id.tvWrongUpdate).setOnClickListener(v -> startEditing());
        findViewById(R.id.btnParkedYes).setOnClickListener(v -> startEditing());
        findViewById(R.id.btnParkedNo).setOnClickListener(v -> finish());

        btnDecrease.setOnClickListener(v -> { pending = pending.previous(); render(); });
        btnIncrease.setOnClickListener(v -> { pending = pending.next(); render(); });
        findViewById(R.id.btnCancel).setOnClickListener(v -> { editing = false; render(); });
        btnSave.setOnClickListener(v -> save());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true)
        {
            @Override
            public void handleOnBackPressed()
            {
                if (editing && !saving)
                {
                    editing = false;
                    render();
                }
                else
                {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        render();
    }

    @Override
    protected void onStart()
    {
        super.onStart();
        if (rackId == null) return;
        ratingsListener = FirebaseFirestore.getInstance()
                .collection("ratings").whereEqualTo("rackId", rackId)
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null) return;
                    String uid = repo.currentUid();
                    int rec = 0, not = 0;
                    String mine = null;
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        String q = d.getString("quality");
                        if (BikeRackRepository.QUALITY_RECOMMENDED.equals(q)) rec++;
                        else if (BikeRackRepository.QUALITY_NOT_RECOMMENDED.equals(q)) not++;
                        if (uid != null && uid.equals(d.getString("userId"))) mine = q;
                    }
                    recommendedCount = rec;
                    notRecommendedCount = not;
                    myRating = mine;
                    renderRating();
                });
        listener = FirebaseFirestore.getInstance()
                .collection("racks").document(rackId)
                .addSnapshotListener((doc, error) ->
                {
                    if (error != null || doc == null) return;
                    if (!doc.exists())
                    {
                        Toast.makeText(this, "That rack could not be found.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    currentStatus = RackStatus.fromKey(doc.getString("currentStatus"));
                    Timestamp ts = doc.getTimestamp("lastReportAt",
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
                    lastReport = ts == null ? null : ts.toDate();
                    String name = doc.getString("name");
                    if (name != null) tvRackName.setText(name);
                    render();
                });
    }

    @Override
    protected void onStop()
    {
        super.onStop();
        if (listener != null)
        {
            listener.remove();
            listener = null;
        }
        if (ratingsListener != null)
        {
            ratingsListener.remove();
            ratingsListener = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_EDITING, editing);
        outState.putInt(STATE_PENDING, pending.ordinal());
    }

    private void startEditing()
    {
        if (!repo.isLoggedIn())
        {
            Toast.makeText(this, "Please log in to submit a report.", Toast.LENGTH_LONG).show();
            return;
        }
        pending = currentStatus != null ? currentStatus : RackStatus.HALF;
        editing = true;
        render();
    }

    private void rate(String quality)
    {
        if (!repo.isLoggedIn())
        {
            Toast.makeText(this, "Please log in to rate a rack.", Toast.LENGTH_LONG).show();
            return;
        }
        ratingBusy = true;
        renderRating();

        BikeRackRepository.Callback cb = new BikeRackRepository.Callback()
        {
            @Override
            public void onSuccess()
            {
                ratingBusy = false;
                renderRating();
            }

            @Override
            public void onError(String message)
            {
                ratingBusy = false;
                Toast.makeText(OccupancyActivity.this, message, Toast.LENGTH_LONG).show();
                renderRating();
            }
        };

        if (quality.equals(myRating))
        {
            repo.clearRating(rackId, cb);
        }
        else
        {
            repo.setRating(rackId, quality, cb);
        }
    }

    private void renderRating()
    {
        boolean rec = BikeRackRepository.QUALITY_RECOMMENDED.equals(myRating);
        boolean not = BikeRackRepository.QUALITY_NOT_RECOMMENDED.equals(myRating);
        styleRatingButton(btnRecommend, "Recommended", rec, Color.parseColor("#2E7D32"));
        styleRatingButton(btnNotRecommend, "Not recommended", not, Color.parseColor("#C62828"));

        int total = recommendedCount + notRecommendedCount;
        tvRatingSummary.setText(total == 0
                ? "No ratings yet"
                : recommendedCount + " recommended \u00b7 " + notRecommendedCount + " not recommended");
    }

    private void styleRatingButton(Button button, String label, boolean selected, int color)
    {
        button.setEnabled(!ratingBusy);
        button.setText(selected ? "\u2713 " + label : label);
        button.setBackgroundTintList(selected ? ColorStateList.valueOf(color) : null);
        button.setTextColor(selected
                ? Color.WHITE
                : ContextCompat.getColor(this, R.color.text_primary));
    }

    private void save()
    {
        saving = true;
        render();
        repo.submitReport(rackId, pending.key, new BikeRackRepository.Callback()
        {
            @Override
            public void onSuccess()
            {
                saving = false;
                editing = false;
                Toast.makeText(OccupancyActivity.this, "Report saved. Thanks!", Toast.LENGTH_SHORT).show();
                render();
            }

            @Override
            public void onError(String message)
            {
                saving = false;
                Toast.makeText(OccupancyActivity.this, message, Toast.LENGTH_LONG).show();
                render();
            }
        });
    }

    private void render()
    {
        RackStatus shown = editing ? pending : currentStatus;

        if (shown == null)
        {
            tvLevel.setText("No reports yet");
            bar.setProgress(0);
            bar.setProgressTintList(ColorStateList.valueOf(RackStatus.NO_DATA_COLOR));
        }
        else
        {
            tvLevel.setText(shown.label);
            bar.setProgress(shown.progress);
            bar.setProgressTintList(ColorStateList.valueOf(shown.color));
        }

        tvHeading.setText(editing ? "Your Report:" : "Current Occupancy:");

        if (editing)
        {
            tvLastReported.setVisibility(View.GONE);
        }
        else
        {
            tvLastReported.setVisibility(View.VISIBLE);
            if (lastReport == null)
            {
                tvLastReported.setText("Be the first to report this rack");
            }
            else
            {
                CharSequence ago = DateUtils.getRelativeTimeSpanString(
                        lastReport.getTime(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
                tvLastReported.setText("Last reported " + ago);
            }
        }
        ratingPanel.setVisibility(editing ? View.GONE : View.VISIBLE);
        renderRating();
        viewPanel.setVisibility(editing ? View.GONE : View.VISIBLE);
        updatePanel.setVisibility(editing ? View.VISIBLE : View.GONE);

        btnDecrease.setEnabled(!saving && pending.ordinal() > 0);
        btnIncrease.setEnabled(!saving && pending.ordinal() < RackStatus.values().length - 1);
        btnSave.setEnabled(!saving);
        btnSave.setText(saving ? "Saving..." : "Save");
    }
}