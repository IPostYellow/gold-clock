package com.example.goldclock;

public class GoldPrice {
    public final double usdPerOunce;
    public final double cnyPerGram;
    public final double usdToCny;
    public final long fetchedAtMillis;
    public final String sourceName;

    public GoldPrice(double usdPerOunce, double cnyPerGram, double usdToCny, long fetchedAtMillis) {
        this(usdPerOunce, cnyPerGram, usdToCny, fetchedAtMillis, "Unknown");
    }

    public GoldPrice(double usdPerOunce, double cnyPerGram, double usdToCny, long fetchedAtMillis, String sourceName) {
        this.usdPerOunce = usdPerOunce;
        this.cnyPerGram = cnyPerGram;
        this.usdToCny = usdToCny;
        this.fetchedAtMillis = fetchedAtMillis;
        this.sourceName = sourceName;
    }
}
