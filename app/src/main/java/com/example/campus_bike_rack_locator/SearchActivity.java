package com.example.campus_bike_rack_locator;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;


public class SearchActivity extends AppCompatActivity
{

    private static final String STATE_FILTER = "filter";
    private static final int FILTER_ALL = 0;
    private static final int FILTER_NO_REPORTS = RackStatus.values().length + 1;

    private static class Rack
    {
        String id;
        String name;
        RackStatus status;
        long lastReport;
    }

    private final List<Rack> allRacks = new ArrayList<>();
    private final List<Rack> shown = new ArrayList<>();
    private final List<TextView> pills = new ArrayList<>();
    private RackAdapter adapter;
    private ListenerRegistration listener;
    private int filter = FILTER_ALL;

    private EditText etSearch;
    private TextView tvResultCount, tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        if (savedInstanceState != null)
        {
            filter = savedInstanceState.getInt(STATE_FILTER, FILTER_ALL);
        }

        etSearch = findViewById(R.id.etSearch);
        tvResultCount = findViewById(R.id.tvResultCount);
        tvEmpty = findViewById(R.id.tvEmpty);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnProfile).setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

        etSearch.addTextChangedListener(new TextWatcher()
        {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { applyFilters(); }
        });
        etSearch.setOnEditorActionListener((v, actionId, event) ->
        {
            if (actionId == EditorInfo.IME_ACTION_SEARCH)
            {
                hideKeyboard();
                return true;
            }
            return false;
        });
        findViewById(R.id.btnSearchGo).setOnClickListener(v -> hideKeyboard());

        buildFilterPills();

        adapter = new RackAdapter();
        ListView list = findViewById(R.id.rackList);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) ->
        {
            Rack rack = shown.get(position);
            Intent intent = new Intent(this, OccupancyActivity.class);
            intent.putExtra(OccupancyActivity.EXTRA_RACK_ID, rack.id);
            intent.putExtra(OccupancyActivity.EXTRA_RACK_NAME, rack.name);
            startActivity(intent);
        });
    }

    @Override
    protected void onStart()
    {
        super.onStart();
        listener = FirebaseFirestore.getInstance().collection("racks")
                .addSnapshotListener((snap, error) ->
                {
                    if (error != null)
                    {
                        Toast.makeText(this, "Could not load bike racks.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (snap == null) return;

                    allRacks.clear();
                    for (DocumentSnapshot doc : snap.getDocuments())
                    {
                        Rack r = new Rack();
                        r.id = doc.getId();
                        String name = doc.getString("name");
                        r.name = (name == null || name.trim().isEmpty()) ? "Unnamed rack" : name;
                        r.status = RackStatus.fromKey(doc.getString("currentStatus"));
                        Timestamp ts = doc.getTimestamp("lastReportAt",
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
                        r.lastReport = ts == null ? 0 : ts.toDate().getTime();
                        allRacks.add(r);
                    }
                    Collections.sort(allRacks, (a, b) -> a.name.compareToIgnoreCase(b.name));
                    applyFilters();
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
    }

    @Override
    protected void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_FILTER, filter);
    }

    private void buildFilterPills()
    {
        LinearLayout row = findViewById(R.id.filterRow);
        List<String> labels = new ArrayList<>();
        labels.add("All");
        for (RackStatus s : RackStatus.values()) labels.add(s.label);
        labels.add("No reports");

        for (int i = 0; i < labels.size(); i++)
        {
            final int index = i;
            TextView pill = new TextView(this);
            pill.setText(labels.get(i));
            pill.setTextSize(14);
            pill.setGravity(Gravity.CENTER);
            pill.setMinHeight(dp(48));
            pill.setPadding(dp(16), 0, dp(16), 0);
            pill.setBackgroundResource(R.drawable.bg_gray_button);
            pill.setClickable(true);
            pill.setFocusable(true);
            pill.setOnClickListener(v ->
            {
                filter = index;
                stylePills();
                applyFilters();
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            row.addView(pill, lp);
            pills.add(pill);
        }
        stylePills();
    }

    private void stylePills()
    {
        int green = ContextCompat.getColor(this, R.color.accent_green);
        int dark = ContextCompat.getColor(this, R.color.text_primary);
        for (int i = 0; i < pills.size(); i++)
        {
            TextView p = pills.get(i);
            boolean selected = i == filter;
            p.setBackgroundTintList(selected ? ColorStateList.valueOf(green) : null);
            p.setTextColor(selected ? Color.WHITE : dark);
            p.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private boolean matchesFilter(Rack r)
    {
        if (filter == FILTER_ALL) return true;
        if (filter == FILTER_NO_REPORTS) return r.status == null;
        return r.status != null && r.status.ordinal() == filter - 1;
    }

    private void applyFilters()
    {
        String q = etSearch.getText().toString().trim().toLowerCase(Locale.US);

        shown.clear();
        for (Rack r : allRacks)
        {
            if (!matchesFilter(r)) continue;
            if (!q.isEmpty())
            {
                String statusText = r.status == null ? "no reports" : r.status.label.toLowerCase(Locale.US);
                boolean hit = r.name.toLowerCase(Locale.US).contains(q) || statusText.contains(q);
                if (!hit) continue;
            }
            shown.add(r);
        }

        adapter.notifyDataSetChanged();
        tvResultCount.setText(shown.size() + (shown.size() == 1 ? " rack" : " racks"));
        tvEmpty.setVisibility(shown.isEmpty() && !allRacks.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void hideKeyboard()
    {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
        etSearch.clearFocus();
    }

    private int dp(int value)
    {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private class RackAdapter extends BaseAdapter
    {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int position) { return shown.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            View row = convertView != null
                    ? convertView
                    : LayoutInflater.from(SearchActivity.this).inflate(R.layout.item_rack_row, parent, false);

            Rack rack = shown.get(position);
            row.setBackgroundResource(position % 2 == 0 ? R.drawable.bg_rack_row : R.drawable.bg_rack_row_alt);

            ((TextView) row.findViewById(R.id.tvRackRowName)).setText(rack.name);

            int color;
            String statusText;
            if (rack.status == null)
            {
                color = RackStatus.NO_DATA_COLOR;
                statusText = "No reports yet";
            }
            else
            {
                color = rack.status.color;
                statusText = rack.status.label;
                if (rack.lastReport > 0)
                {
                    CharSequence ago = DateUtils.getRelativeTimeSpanString(
                            rack.lastReport, System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE);
                    statusText += " \u00b7 " + ago;
                }
            }
            ((TextView) row.findViewById(R.id.tvRackRowStatus)).setText(statusText);
            row.findViewById(R.id.viewStatusDot).setBackgroundTintList(ColorStateList.valueOf(color));
            return row;
        }
    }
}