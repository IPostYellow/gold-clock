package com.example.goldclock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GoldPriceRepository {
    private static final String CONVERTZ_METALS_URL = "https://convertz.app/api/metals";
    private static final String CONVERTZ_CURRENCY_URL = "https://convertz.app/api/currency";
    private static final String EXCHANGE_RATE_URL = "https://open.er-api.com/v6/latest/USD";
    private static final String GOLD_API_XAU_URL = "https://api.gold-api.com/price/XAU";
    private static final String VANG_TODAY_XAU_URL = "https://www.vang.today/api/prices?type=XAUUSD";
    private static final String VANG_TODAY_HISTORY_URL = "https://www.vang.today/api/prices?type=XAUUSD&days=30";
    private static final String GOLD_FUTURES_CHART_URL =
            "https://query1.finance.yahoo.com/v8/finance/chart/GC=F?range=3mo&interval=1d";
    private static final double TROY_OUNCE_GRAMS = 31.1034768d;

    public GoldPrice fetchPrice() throws IOException, JSONException {
        StringBuilder errors = new StringBuilder();

        try {
            return fetchFromConvertz();
        } catch (Exception exception) {
            appendError(errors, "Convertz", exception);
        }

        try {
            return buildPrice(fetchGoldApiUsdPerOunce(), "Gold API");
        } catch (Exception exception) {
            appendError(errors, "Gold API", exception);
        }

        try {
            return buildPrice(fetchVangTodayUsdPerOunce(), "Vang Today");
        } catch (Exception exception) {
            appendError(errors, "Vang Today", exception);
        }

        throw new IOException("All gold price sources failed: " + errors);
    }

    public KLineSeries fetchKLineSeries() throws IOException, JSONException {
        StringBuilder errors = new StringBuilder();
        try {
            return new KLineSeries(fetchYahooKLineEntries(), "Yahoo Finance · GC=F");
        } catch (Exception exception) {
            appendError(errors, "Yahoo Finance", exception);
        }

        try {
            return new KLineSeries(fetchVangTodayKLineEntries(), "Vang Today · 近似日 K");
        } catch (Exception exception) {
            appendError(errors, "Vang Today K", exception);
        }

        throw new IOException("All chart sources failed: " + errors);
    }

    private List<KLineEntry> fetchYahooKLineEntries() throws IOException, JSONException {
        JSONObject root = readJson(GOLD_FUTURES_CHART_URL);
        JSONObject chart = root.optJSONObject("chart");
        if (chart == null) {
            throw new JSONException("Missing chart data");
        }
        if (!chart.isNull("error")) {
            throw new JSONException("Chart API error: " + chart.opt("error"));
        }

        JSONArray results = chart.optJSONArray("result");
        JSONObject result = results == null ? null : results.optJSONObject(0);
        if (result == null) {
            throw new JSONException("Missing chart result");
        }

        JSONArray timestamps = result.optJSONArray("timestamp");
        JSONObject indicators = result.optJSONObject("indicators");
        JSONArray quotes = indicators == null ? null : indicators.optJSONArray("quote");
        JSONObject quote = quotes == null ? null : quotes.optJSONObject(0);
        if (timestamps == null || quote == null) {
            throw new JSONException("Missing OHLC arrays");
        }

        JSONArray opens = quote.optJSONArray("open");
        JSONArray highs = quote.optJSONArray("high");
        JSONArray lows = quote.optJSONArray("low");
        JSONArray closes = quote.optJSONArray("close");

        List<KLineEntry> entries = new ArrayList<>();
        int count = timestamps.length();
        for (int i = 0; i < count; i++) {
            double open = optFiniteDouble(opens, i);
            double high = optFiniteDouble(highs, i);
            double low = optFiniteDouble(lows, i);
            double close = optFiniteDouble(closes, i);
            if (Double.isNaN(open) || Double.isNaN(high) || Double.isNaN(low) || Double.isNaN(close)) {
                continue;
            }
            entries.add(new KLineEntry(timestamps.optLong(i), open, high, low, close));
        }

        if (entries.isEmpty()) {
            throw new JSONException("No valid OHLC entries");
        }
        return entries;
    }

    private List<KLineEntry> fetchVangTodayKLineEntries() throws IOException, JSONException {
        JSONObject root = readJson(VANG_TODAY_HISTORY_URL);
        if (!root.optBoolean("success", false)) {
            throw new JSONException("Vang Today history request failed");
        }

        JSONArray history = root.optJSONArray("history");
        if (history == null || history.length() == 0) {
            throw new JSONException("Missing Vang Today history");
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        List<KLineEntry> entries = new ArrayList<>();
        for (int i = history.length() - 1; i >= 0; i--) {
            JSONObject day = history.optJSONObject(i);
            if (day == null) {
                continue;
            }
            JSONObject prices = day.optJSONObject("prices");
            JSONObject xau = prices == null ? null : prices.optJSONObject("XAUUSD");
            if (xau == null) {
                continue;
            }

            double close = xau.optDouble("buy", 0d);
            double change = xau.optDouble("day_change_buy", 0d);
            if (close <= 0d) {
                continue;
            }

            long timestampSeconds;
            try {
                Date date = dateFormat.parse(day.optString("date"));
                if (date == null) {
                    continue;
                }
                timestampSeconds = date.getTime() / 1000L;
            } catch (ParseException exception) {
                continue;
            }

            double open = close - change;
            double high = Math.max(open, close);
            double low = Math.min(open, close);
            entries.add(new KLineEntry(timestampSeconds, open, high, low, close));
        }

        if (entries.isEmpty()) {
            throw new JSONException("No valid Vang Today entries");
        }
        return entries;
    }

    private GoldPrice fetchFromConvertz() throws IOException, JSONException {
        JSONObject metals = readJson(CONVERTZ_METALS_URL);
        return buildPrice(parseConvertzUsdPerOunce(metals), "Convertz");
    }

    private GoldPrice buildPrice(double usdPerOunce, String sourceName) throws IOException, JSONException {
        double usdToCny = fetchUsdToCny();

        if (usdPerOunce <= 0d) {
            throw new JSONException("Invalid gold price");
        }
        if (usdToCny <= 0d) {
            throw new JSONException("Invalid USD/CNY rate");
        }

        double cnyPerGram = usdPerOunce * usdToCny / TROY_OUNCE_GRAMS;
        return new GoldPrice(
                usdPerOunce,
                cnyPerGram,
                usdToCny,
                System.currentTimeMillis(),
                sourceName);
    }

    private double fetchUsdToCny() throws IOException, JSONException {
        StringBuilder errors = new StringBuilder();

        try {
            return parseUsdToCny(readJson(CONVERTZ_CURRENCY_URL));
        } catch (Exception exception) {
            appendError(errors, "Convertz FX", exception);
        }

        try {
            return parseUsdToCny(readJson(EXCHANGE_RATE_URL));
        } catch (Exception exception) {
            appendError(errors, "ExchangeRate API", exception);
        }

        throw new IOException("All currency sources failed: " + errors);
    }

    private double parseUsdToCny(JSONObject currency) throws JSONException {
        JSONObject rates = currency.optJSONObject("rates");
        double usdToCny = rates == null ? 0d : rates.optDouble("CNY", 0d);
        if (usdToCny <= 0d && rates != null) {
            usdToCny = rates.optDouble("CNH", 0d);
        }
        if (usdToCny <= 0d) {
            throw new JSONException("Invalid USD/CNY rate");
        }
        return usdToCny;
    }

    private double parseConvertzUsdPerOunce(JSONObject metals) throws JSONException {
        JSONObject legacyXau = metals.optJSONObject("XAU");
        if (legacyXau != null) {
            double price = legacyXau.optDouble("price", 0d);
            if (price > 0d) {
                return price;
            }
        }

        JSONObject prices = metals.optJSONObject("prices");
        JSONObject xauPrices = prices == null ? null : prices.optJSONObject("XAU");
        if (xauPrices != null) {
            double price = xauPrices.optDouble("USD", 0d);
            if (price > 0d) {
                return price;
            }
        }

        JSONArray metalsArray = metals.optJSONArray("metals");
        if (metalsArray != null) {
            for (int i = 0; i < metalsArray.length(); i++) {
                JSONObject metal = metalsArray.optJSONObject(i);
                if (metal == null || !"XAU".equalsIgnoreCase(metal.optString("symbol"))) {
                    continue;
                }
                JSONObject price = metal.optJSONObject("price");
                double usd = price == null ? 0d : price.optDouble("USD", 0d);
                if (usd > 0d) {
                    return usd;
                }
            }
        }

        throw new JSONException("Missing XAU quote");
    }

    private double fetchGoldApiUsdPerOunce() throws IOException, JSONException {
        JSONObject quote = readJson(GOLD_API_XAU_URL);
        double price = quote.optDouble("price", 0d);
        if (price <= 0d) {
            throw new JSONException("Invalid Gold API price");
        }
        return price;
    }

    private double fetchVangTodayUsdPerOunce() throws IOException, JSONException {
        JSONObject quote = readJson(VANG_TODAY_XAU_URL);
        if (!quote.optBoolean("success", false)) {
            throw new JSONException("Vang Today request failed");
        }

        double price = quote.optDouble("buy", 0d);
        if (price <= 0d) {
            throw new JSONException("Invalid Vang Today price");
        }
        return price;
    }

    private JSONObject readJson(String urlString) throws IOException, JSONException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json, text/plain, */*");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 GoldClock/1.0");
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

    private double optFiniteDouble(JSONArray array, int index) {
        if (array == null || index >= array.length() || array.isNull(index)) {
            return Double.NaN;
        }
        double value = array.optDouble(index, Double.NaN);
        return Double.isInfinite(value) ? Double.NaN : value;
    }

    private void appendError(StringBuilder builder, String source, Exception exception) {
        if (builder.length() > 0) {
            builder.append("; ");
        }
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = exception.getClass().getSimpleName();
        }
        builder.append(source).append(": ").append(message);
    }
}
