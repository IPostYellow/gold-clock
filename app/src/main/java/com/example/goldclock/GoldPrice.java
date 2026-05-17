package com.example.goldclock;

public class GoldPrice {
    public final double usdPerOunce;
    public final double cnyPerGram;
    public final double usdToCny;
    public final long fetchedAtMillis;

    public GoldPrice(double usdPerOunce, double cnyPerGram, double usdToCny, long fetchedAtMillis) {
        this.usdPerOunce = usdPerOunce;
        this.cnyPerGram = cnyPerGram;
        this.usdToCny = usdToCny;
        this.fetchedAtMillis = fetchedAtMillis;
    }
}
