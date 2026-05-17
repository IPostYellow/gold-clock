package com.example.goldclock;

public class KLineEntry {
    public final long timestampSeconds;
    public final double open;
    public final double high;
    public final double low;
    public final double close;

    public KLineEntry(long timestampSeconds, double open, double high, double low, double close) {
        this.timestampSeconds = timestampSeconds;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
    }

    public boolean isRising() {
        return close >= open;
    }
}
