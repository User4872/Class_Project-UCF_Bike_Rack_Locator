package com.example.campus_bike_rack_locator;

import android.graphics.Color;

public enum RackStatus {
    EMPTY("empty", "Empty", 20, Color.parseColor("#2E7D32")),
    LOW("low", "Low", 40, Color.parseColor("#7CB342")),
    HALF("half", "Half Full", 60, Color.parseColor("#F9A825")),
    NEARLY_FULL("nearly_full", "Nearly Full", 80, Color.parseColor("#EF6C00")),
    FULL("full", "Full", 100, Color.parseColor("#C62828"));

    public static final int NO_DATA_COLOR = Color.parseColor("#757575");

    public final String key;
    public final String label;
    public final int progress;
    public final int color;

    RackStatus(String key, String label, int progress, int color)
    {
        this.key = key;
        this.label = label;
        this.progress = progress;
        this.color = color;
    }

    public static RackStatus fromKey(String key)
    {
        if (key == null) return null;
        for (RackStatus s : values())
        {
            if (s.key.equals(key)) return s;
        }
        return null;
    }

    public RackStatus next()
    {
        RackStatus[] all = values();
        return all[Math.min(ordinal() + 1, all.length - 1)];
    }

    public RackStatus previous()
    {
        return values()[Math.max(ordinal() - 1, 0)];
    }
}
