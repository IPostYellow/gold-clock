package com.example.goldclock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PriceMonitorService extends Service {
    public static final String ACTION_START = "com.example.goldclock.action.START_MONITOR";
    public static final String ACTION_STOP = "com.example.goldclock.action.STOP_MONITOR";
    public static final String ACTION_PRICE_UPDATED = "com.example.goldclock.action.PRICE_UPDATED";
    public static final String ACTION_PRICE_ERROR = "com.example.goldclock.action.PRICE_ERROR";

    public static final String EXTRA_USD_PER_OUNCE = "usd_per_ounce";
    public static final String EXTRA_CNY_PER_GRAM = "cny_per_gram";
    public static final String EXTRA_USD_TO_CNY = "usd_to_cny";
    public static final String EXTRA_FETCHED_AT = "fetched_at";
    public static final String EXTRA_SOURCE = "source";
    public static final String EXTRA_ERROR = "error";

    private static final String CHANNEL_MONITOR = "gold_clock_monitor";
    private static final int MONITOR_NOTIFICATION_ID = 1001;
    private static final long POLL_INTERVAL_MS = 60_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Runnable pollRunnable = this::pollNow;

    private GoldPriceRepository repository;
    private AlertStorage storage;
    private volatile boolean running;
    private volatile boolean polling;

    @Override
    public void onCreate() {
        super.onCreate();
        repository = new GoldPriceRepository();
        storage = new AlertStorage(this);
        createNotificationChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopMonitor();
            return START_NOT_STICKY;
        }

        running = true;
        startForeground(MONITOR_NOTIFICATION_ID, buildMonitorNotification("正在准备刷新"));
        pollNow();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        super.onDestroy();
    }

    private void pollNow() {
        handler.removeCallbacks(pollRunnable);
        if (!running || polling) {
            return;
        }

        polling = true;
        updateMonitorNotification("正在刷新金价");
        executor.execute(() -> {
            try {
                GoldPrice price = repository.fetchPrice();
                evaluateAlerts(price);
                sendPriceUpdate(price);
                updateMonitorNotification(String.format(
                        Locale.CHINA,
                        "最新 ¥%,.2f/克",
                        price.cnyPerGram));
            } catch (Exception exception) {
                String message = readableError(exception);
                sendPriceError(message);
                updateMonitorNotification("刷新失败：" + message);
            } finally {
                polling = false;
                if (running) {
                    handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
                }
            }
        });
    }

    private void evaluateAlerts(GoldPrice price) {
        List<GoldAlert> alerts = storage.loadAlerts();
        if (alerts.isEmpty()) {
            return;
        }

        for (GoldAlert alert : alerts) {
            if (alert.updateAndShouldTrigger(price)) {
                AlertNotifier.sendAlertNotification(this, alert, price);
            }
        }
        storage.saveAlerts(alerts);
    }

    private Notification buildMonitorNotification(String contentText) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                pendingIntentFlags());

        return new Notification.Builder(this, CHANNEL_MONITOR)
                .setSmallIcon(R.drawable.ic_stat_gold_clock)
                .setContentTitle("Gold Clock 正在监测")
                .setContentText(contentText)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void updateMonitorNotification(String contentText) {
        NotificationManager manager = notificationManager();
        manager.notify(MONITOR_NOTIFICATION_ID, buildMonitorNotification(contentText));
    }

    private void createNotificationChannels() {
        NotificationChannel monitor = new NotificationChannel(
                CHANNEL_MONITOR,
                "Gold Clock 后台监测",
                NotificationManager.IMPORTANCE_LOW);
        monitor.setDescription("显示 Gold Clock 正在后台刷新金价。");

        NotificationManager manager = notificationManager();
        manager.createNotificationChannel(monitor);
        AlertNotifier.createAlertChannel(this);
    }

    private void sendPriceUpdate(GoldPrice price) {
        Intent intent = new Intent(ACTION_PRICE_UPDATED);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_USD_PER_OUNCE, price.usdPerOunce);
        intent.putExtra(EXTRA_CNY_PER_GRAM, price.cnyPerGram);
        intent.putExtra(EXTRA_USD_TO_CNY, price.usdToCny);
        intent.putExtra(EXTRA_FETCHED_AT, price.fetchedAtMillis);
        intent.putExtra(EXTRA_SOURCE, price.sourceName);
        sendBroadcast(intent);
    }

    private void sendPriceError(String message) {
        Intent intent = new Intent(ACTION_PRICE_ERROR);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_ERROR, message);
        sendBroadcast(intent);
    }

    private void stopMonitor() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        stopForeground(true);
        stopSelf();
    }

    private NotificationManager notificationManager() {
        return (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    private String readableError(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 80 ? message.substring(0, 80) : message;
    }
}
