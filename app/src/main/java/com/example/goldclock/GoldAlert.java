package com.example.goldclock;

import org.json.JSONException;
import org.json.JSONObject;

public class GoldAlert {
    public static final String CONDITION_ABOVE = "ABOVE";
    public static final String CONDITION_BELOW = "BELOW";
    public static final String UNIT_CNY_GRAM = "CNY_GRAM";
    public static final String UNIT_USD_OUNCE = "USD_OUNCE";

    public String id;
    public String condition;
    public String unit;
    public double threshold;
    public boolean enabled;
    public boolean inTriggeredState;
    public long lastTriggeredAtMillis;

    public GoldAlert() {
        id = String.valueOf(System.currentTimeMillis());
        condition = CONDITION_ABOVE;
        unit = UNIT_CNY_GRAM;
        enabled = true;
    }

    public double currentValue(GoldPrice price) {
        if (UNIT_USD_OUNCE.equals(unit)) {
            return price.usdPerOunce;
        }
        return price.cnyPerGram;
    }

    public boolean isCurrentlyTriggered(GoldPrice price) {
        double current = currentValue(price);
        if (CONDITION_BELOW.equals(condition)) {
            return current <= threshold;
        }
        return current >= threshold;
    }

    public boolean updateAndShouldTrigger(GoldPrice price) {
        if (!enabled) {
            inTriggeredState = false;
            return false;
        }

        boolean nowTriggered = isCurrentlyTriggered(price);
        boolean shouldNotify = nowTriggered && !inTriggeredState;
        inTriggeredState = nowTriggered;
        if (shouldNotify) {
            lastTriggeredAtMillis = System.currentTimeMillis();
        }
        return shouldNotify;
    }

    public JSONObject toJson() {
        JSONObject object = new JSONObject();
        try {
            object.put("id", id);
            object.put("condition", condition);
            object.put("unit", unit);
            object.put("threshold", threshold);
            object.put("enabled", enabled);
            object.put("inTriggeredState", inTriggeredState);
            object.put("lastTriggeredAtMillis", lastTriggeredAtMillis);
        } catch (JSONException ignored) {
            // JSONObject only throws for unsupported values; all fields here are primitives or strings.
        }
        return object;
    }

    public static GoldAlert fromJson(JSONObject object) {
        GoldAlert alert = new GoldAlert();
        alert.id = object.optString("id", alert.id);
        alert.condition = object.optString("condition", CONDITION_ABOVE);
        alert.unit = object.optString("unit", UNIT_CNY_GRAM);
        alert.threshold = object.optDouble("threshold", 0d);
        alert.enabled = object.optBoolean("enabled", true);
        alert.inTriggeredState = object.optBoolean("inTriggeredState", false);
        alert.lastTriggeredAtMillis = object.optLong("lastTriggeredAtMillis", 0L);
        return alert;
    }
}
