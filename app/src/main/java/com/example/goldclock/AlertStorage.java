package com.example.goldclock;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class AlertStorage {
    private static final String PREFS_NAME = "gold_clock";
    private static final String KEY_ALERTS = "alerts";
    private static final String KEY_MONITORING_ENABLED = "monitoring_enabled";

    private final SharedPreferences preferences;

    public AlertStorage(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public List<GoldAlert> loadAlerts() {
        String raw = preferences.getString(KEY_ALERTS, "[]");
        List<GoldAlert> alerts = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object != null) {
                    GoldAlert alert = GoldAlert.fromJson(object);
                    if (alert.threshold > 0d) {
                        alerts.add(alert);
                    }
                }
            }
        } catch (JSONException ignored) {
            preferences.edit().putString(KEY_ALERTS, "[]").apply();
        }
        return alerts;
    }

    public void saveAlerts(List<GoldAlert> alerts) {
        JSONArray array = new JSONArray();
        for (GoldAlert alert : alerts) {
            array.put(alert.toJson());
        }
        preferences.edit().putString(KEY_ALERTS, array.toString()).apply();
    }

    public boolean isMonitoringEnabled() {
        return preferences.getBoolean(KEY_MONITORING_ENABLED, false);
    }

    public void setMonitoringEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_MONITORING_ENABLED, enabled).apply();
    }
}
