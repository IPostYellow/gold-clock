package com.example.goldclock;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class GoldPriceRepository {
    private static final String METALS_URL = "https://convertz.app/api/metals";
    private static final String CURRENCY_URL = "https://convertz.app/api/currency";
    private static final double TROY_OUNCE_GRAMS = 31.1034768d;

    public GoldPrice fetchPrice() throws IOException, JSONException {
        JSONObject metals = readJson(METALS_URL);
        JSONObject currency = readJson(CURRENCY_URL);

        JSONObject xau = metals.optJSONObject("XAU");
        if (xau == null) {
            throw new JSONException("Missing XAU quote");
        }

        double usdPerOunce = xau.optDouble("price", 0d);
        JSONObject rates = currency.optJSONObject("rates");
        double usdToCny = rates == null ? 0d : rates.optDouble("CNY", 0d);

        if (usdPerOunce <= 0d) {
            throw new JSONException("Invalid gold price");
        }
        if (usdToCny <= 0d) {
            throw new JSONException("Invalid USD/CNY rate");
        }

        double cnyPerGram = usdPerOunce * usdToCny / TROY_OUNCE_GRAMS;
        return new GoldPrice(usdPerOunce, cnyPerGram, usdToCny, System.currentTimeMillis());
    }

    private JSONObject readJson(String urlString) throws IOException, JSONException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);

            int responseCode = connection.getResponseCode();
            InputStream stream = responseCode >= 200 && responseCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = readBody(stream);

            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("HTTP " + responseCode + ": " + body);
            }

            return new JSONObject(body);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readBody(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[2048];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, count);
            }
        }
        return builder.toString();
    }
}
