package com.example.campus_bike_rack_locator;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback{

    private static final LatLng CAMPUS = new LatLng(28.6024, -81.2001);

    private GoogleMap map;
    private boolean firstLaunch;
    private ListenerRegistration racksListener;

    private final Map<String, Marker> markers = new HashMap<>();
    private final Map<Integer, BitmapDescriptor> iconCache = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        firstLaunch = savedInstanceState == null;

        findViewById(R.id.btnProfile).setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));
        findViewById(R.id.btnSearch).setOnClickListener(v ->
                startActivity(new Intent(this, SearchActivity.class)));

        FragmentManager fm = getSupportFragmentManager();
        SupportMapFragment mapFragment = (SupportMapFragment) fm.findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap)
    {
        map = googleMap;
        if (firstLaunch)
        {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(CAMPUS, 16f));
        }
        map.getUiSettings().setMapToolbarEnabled(false);

        map.setOnMarkerClickListener(marker ->
        {
            Object id = marker.getTag();
            if (id instanceof String)
            {
                Intent intent = new Intent(this, OccupancyActivity.class);
                intent.putExtra(OccupancyActivity.EXTRA_RACK_ID, (String) id);
                intent.putExtra(OccupancyActivity.EXTRA_RACK_NAME, marker.getTitle());
                startActivity(intent);
            }
            return true;
        });

        startListening();
    }

    @Override
    protected void onStart()
    {
        super.onStart();
        if (map != null) startListening();
    }

    @Override
    protected void onStop()
    {
        super.onStop();
        stopListening();
    }

    private void startListening()
    {
        if (racksListener != null) return;
        racksListener = FirebaseFirestore.getInstance()
                .collection("racks")
                .addSnapshotListener((snapshot, error) ->
                {
                    if (error != null)
                    {
                        Toast.makeText(this, "Could not load bike racks.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (snapshot != null) syncMarkers(snapshot);
                });
    }

    private void stopListening()
    {
        if (racksListener != null)
        {
            racksListener.remove();
            racksListener = null;
        }
    }

    private void syncMarkers(QuerySnapshot snapshot)
    {
        Set<String> seen = new HashSet<>();

        for (DocumentSnapshot doc : snapshot.getDocuments())
        {
            Double lat = doc.getDouble("latitude");
            Double lng = doc.getDouble("longitude");
            if (lat == null || lng == null) continue;

            String id = doc.getId();
            String name = doc.getString("name");
            RackStatus status = RackStatus.fromKey(doc.getString("currentStatus"));
            LatLng position = new LatLng(lat, lng);
            BitmapDescriptor icon = iconFor(status);

            Marker marker = markers.get(id);
            if (marker == null)
            {
                marker = map.addMarker(new MarkerOptions()
                        .position(position)
                        .title(name)
                        .icon(icon)
                        .anchor(0.5f, 0.5f));
                if (marker != null)
                {
                    marker.setTag(id);
                    markers.put(id, marker);
                }
            }
            else
            {
                marker.setPosition(position);
                marker.setTitle(name);
                marker.setIcon(icon);
            }
            seen.add(id);
        }

        Iterator<Map.Entry<String, Marker>> it = markers.entrySet().iterator();
        while (it.hasNext())
        {
            Map.Entry<String, Marker> entry = it.next();
            if (!seen.contains(entry.getKey()))
            {
                entry.getValue().remove();
                it.remove();
            }
        }
    }

    private BitmapDescriptor iconFor(RackStatus status)
    {
        int color = status == null ? RackStatus.NO_DATA_COLOR : status.color;
        BitmapDescriptor icon = iconCache.get(color);
        if (icon == null)
        {
            icon = BitmapDescriptorFactory.fromBitmap(makeDot(color));
            iconCache.put(color, icon);
        }
        return icon;
    }

    private Bitmap makeDot(int color)
    {
        float density = getResources().getDisplayMetrics().density;
        int size = (int) (28 * density);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float radius = size / 2f;

        paint.setColor(Color.WHITE);
        canvas.drawCircle(radius, radius, radius, paint);
        paint.setColor(color);
        canvas.drawCircle(radius, radius, radius - 3 * density, paint);
        return bitmap;
    }
}