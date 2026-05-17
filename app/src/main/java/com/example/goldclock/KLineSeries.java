package com.example.goldclock;

import java.util.List;

public class KLineSeries {
    public final List<KLineEntry> entries;
    public final String sourceName;

    public KLineSeries(List<KLineEntry> entries, String sourceName) {
        this.entries = entries;
        this.sourceName = sourceName;
    }
}
