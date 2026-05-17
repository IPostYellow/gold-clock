package com.example.goldclock;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import java.util.Locale;

public class AlertNotifier {
    private static final String CHANNEL_ALERTS = "gold_clock_alerts_v2";

    public static void createAlertChannel(Context context) {
        NotificationChannel alerts = new NotificationChannel(
                CHANNEL_ALERTS,
                "Gold Clock 金价闹钟",
                NotificationManager.IMPORTANCE_HIGH);
        alerts.setDescription("价格触发预警时使用闹钟铃声提醒。");
        alerts.enableVibration(true);
        alerts.setSound(defaultAlertSound(), alertAudioAttributes());
        notificationManager(context).createNotificationChannel(alerts);
    }

    public static boolean sendAlertNotification(Context context, GoldAlert alert, GoldPrice price) {
        String threshold = formatValue(alert.unit, alert.threshold);
        String current = formatValue(alert.unit, alert.currentValue(price));
        String direction = GoldAlert.CONDITION_BELOW.equals(alert.condition)
                ? "下跌到或低于"
                : "上涨到或高于";
        String body = "目标 " + direction + " " + threshold + "，当前 " + current;
        return sendNotification(context, "金价闹钟", body);
    }

    public static boolean sendTestNotification(Context context) {
        return sendNotification(context, "Gold Clock 测试提醒", "如果你听到了铃声，通知权限和闹钟渠道是可用的。");
    }

    private static boolean sendNotification(Context context, String title, String body) {
        if (!canPostNotifications(context)) {
            return false;
        }

        createAlertChannel(context);
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                (int) System.currentTimeMillis(),
                intent,
                pendingIntentFlags());

        Notification notification = new Notification.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_gold_clock)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build();

        notificationManager(context).notify((int) (System.currentTimeMillis() % Integer.MAX_VALUE), notification);
        return true;
    }

    private static boolean canPostNotifications(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static NotificationManager notificationManager(Context context) {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private static Uri defaultAlertSound() {
        Uri alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarm != null) {
            return alarm;
        }
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
    }

    private static AudioAttributes alertAudioAttributes() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
    }

    private static int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    private static String formatValue(String unit, double value) {
        if (GoldAlert.UNIT_USD_OUNCE.equals(unit)) {
            return String.format(Locale.CHINA, "$%,.2f/金衡盎司", value);
        }
        return String.format(Locale.CHINA, "¥%,.2f/克", value);
    }
}
