package com.example.goldclock;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 42;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private AlertStorage storage;
    private GoldPriceRepository repository;
    private GoldPrice latestPrice;

    private TextView cnyPriceText;
    private TextView usdPriceText;
    private TextView rateText;
    private TextView statusText;
    private TextView lastUpdatedText;
    private TextView emptyAlertsText;
    private ProgressBar refreshProgress;
    private Button refreshButton;
    private Button addAlertButton;
    private Switch monitorSwitch;
    private Spinner conditionSpinner;
    private Spinner unitSpinner;
    private EditText thresholdInput;
    private LinearLayout alertList;
    private boolean suppressMonitorChange;
    private boolean receiverRegistered;

    private final BroadcastReceiver priceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (PriceMonitorService.ACTION_PRICE_UPDATED.equals(action)) {
                GoldPrice price = new GoldPrice(
                        intent.getDoubleExtra(PriceMonitorService.EXTRA_USD_PER_OUNCE, 0d),
                        intent.getDoubleExtra(PriceMonitorService.EXTRA_CNY_PER_GRAM, 0d),
                        intent.getDoubleExtra(PriceMonitorService.EXTRA_USD_TO_CNY, 0d),
                        intent.getLongExtra(PriceMonitorService.EXTRA_FETCHED_AT, System.currentTimeMillis()),
                        intent.getStringExtra(PriceMonitorService.EXTRA_SOURCE));
                handlePriceUpdate(price);
            } else if (PriceMonitorService.ACTION_PRICE_ERROR.equals(action)) {
                setRefreshing(false);
                statusText.setText("刷新失败：" + intent.getStringExtra(PriceMonitorService.EXTRA_ERROR));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        storage = new AlertStorage(this);
        repository = new GoldPriceRepository();
        bindViews();
        setupInteractions();
        requestNotificationPermissionIfNeeded();

        suppressMonitorChange = true;
        monitorSwitch.setChecked(storage.isMonitoringEnabled());
        suppressMonitorChange = false;

        renderAlerts();
        refreshPrice();

        if (storage.isMonitoringEnabled()) {
            startMonitorService();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(PriceMonitorService.ACTION_PRICE_UPDATED);
        filter.addAction(PriceMonitorService.ACTION_PRICE_ERROR);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(priceReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(priceReceiver, filter);
        }
        receiverRegistered = true;
    }

    @Override
    protected void onStop() {
        if (receiverRegistered) {
            unregisterReceiver(priceReceiver);
            receiverRegistered = false;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void bindViews() {
        cnyPriceText = findViewById(R.id.cnyPriceText);
        usdPriceText = findViewById(R.id.usdPriceText);
        rateText = findViewById(R.id.rateText);
        statusText = findViewById(R.id.statusText);
        lastUpdatedText = findViewById(R.id.lastUpdatedText);
        emptyAlertsText = findViewById(R.id.emptyAlertsText);
        refreshProgress = findViewById(R.id.refreshProgress);
        refreshButton = findViewById(R.id.refreshButton);
        addAlertButton = findViewById(R.id.addAlertButton);
        monitorSwitch = findViewById(R.id.monitorSwitch);
        conditionSpinner = findViewById(R.id.conditionSpinner);
        unitSpinner = findViewById(R.id.unitSpinner);
        thresholdInput = findViewById(R.id.thresholdInput);
        alertList = findViewById(R.id.alertList);
    }

    private void setupInteractions() {
        refreshButton.setOnClickListener(view -> refreshPrice());
        addAlertButton.setOnClickListener(view -> addAlert());
        monitorSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (suppressMonitorChange) {
                return;
            }
            storage.setMonitoringEnabled(checked);
            if (checked) {
                requestNotificationPermissionIfNeeded();
                startMonitorService();
                Toast.makeText(this, "后台监测已开启", Toast.LENGTH_SHORT).show();
            } else {
                stopMonitorService();
                Toast.makeText(this, "后台监测已关闭", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void refreshPrice() {
        setRefreshing(true);
        statusText.setText("正在刷新金价");
        executor.execute(() -> {
            try {
                GoldPrice price = repository.fetchPrice();
                mainHandler.post(() -> {
                    setRefreshing(false);
                    handlePriceUpdate(price);
                });
            } catch (Exception exception) {
                String message = readableError(exception);
                mainHandler.post(() -> {
                    setRefreshing(false);
                    statusText.setText("刷新失败：" + message);
                });
            }
        });
    }

    private void handlePriceUpdate(GoldPrice price) {
        if (price.usdPerOunce <= 0d || price.cnyPerGram <= 0d) {
            statusText.setText("刷新失败：价格数据无效");
            return;
        }

        latestPrice = price;
        cnyPriceText.setText(String.format(Locale.CHINA, "¥%,.2f / 克", price.cnyPerGram));
        usdPriceText.setText(String.format(Locale.CHINA, "$%,.2f / 金衡盎司", price.usdPerOunce));
        rateText.setText(String.format(Locale.CHINA, "USD/CNY %.4f", price.usdToCny));
        statusText.setText("数据源：" + sourceLabel(price.sourceName));
        lastUpdatedText.setText("最后更新：" + DateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.MEDIUM,
                Locale.CHINA).format(new Date(price.fetchedAtMillis)));
        renderAlerts();
    }

    private void addAlert() {
        String rawThreshold = thresholdInput.getText().toString().trim();
        if (rawThreshold.isEmpty()) {
            thresholdInput.setError("请输入预警价格");
            return;
        }

        double threshold;
        try {
            threshold = Double.parseDouble(rawThreshold);
        } catch (NumberFormatException exception) {
            thresholdInput.setError("价格格式不正确");
            return;
        }

        if (threshold <= 0d) {
            thresholdInput.setError("价格必须大于 0");
            return;
        }

        GoldAlert alert = new GoldAlert();
        alert.threshold = threshold;
        alert.condition = conditionSpinner.getSelectedItemPosition() == 0
                ? GoldAlert.CONDITION_ABOVE
                : GoldAlert.CONDITION_BELOW;
        alert.unit = unitSpinner.getSelectedItemPosition() == 0
                ? GoldAlert.UNIT_CNY_GRAM
                : GoldAlert.UNIT_USD_OUNCE;

        List<GoldAlert> alerts = storage.loadAlerts();
        alerts.add(alert);
        storage.saveAlerts(alerts);
        thresholdInput.setText("");
        renderAlerts();
    }

    private void renderAlerts() {
        List<GoldAlert> alerts = storage.loadAlerts();
        alertList.removeAllViews();
        emptyAlertsText.setVisibility(alerts.isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (GoldAlert alert : alerts) {
            View item = inflater.inflate(R.layout.item_alert, alertList, false);
            TextView title = item.findViewById(R.id.alertTitle);
            TextView subtitle = item.findViewById(R.id.alertSubtitle);
            TextView state = item.findViewById(R.id.alertState);
            Switch enabledSwitch = item.findViewById(R.id.alertEnabledSwitch);
            Button deleteButton = item.findViewById(R.id.deleteAlertButton);

            title.setText(conditionLabel(alert.condition) + " " + formatValue(alert.unit, alert.threshold));
            subtitle.setText(unitLabel(alert.unit) + "，首次进入触发区间时通知");
            enabledSwitch.setChecked(alert.enabled);

            if (latestPrice == null) {
                state.setText("等待当前价格");
            } else {
                double current = alert.currentValue(latestPrice);
                String stateText = alert.isCurrentlyTriggered(latestPrice) ? "处于触发区间" : "未触发";
                state.setText("当前 " + formatValue(alert.unit, current) + " · " + stateText);
            }

            enabledSwitch.setOnCheckedChangeListener((button, checked) -> {
                alert.enabled = checked;
                if (!checked) {
                    alert.inTriggeredState = false;
                }
                storage.saveAlerts(alerts);
                renderAlerts();
            });

            deleteButton.setOnClickListener(view -> {
                alerts.remove(alert);
                storage.saveAlerts(alerts);
                renderAlerts();
            });

            alertList.addView(item);
        }
    }

    private void startMonitorService() {
        Intent intent = new Intent(this, PriceMonitorService.class);
        intent.setAction(PriceMonitorService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void stopMonitorService() {
        Intent intent = new Intent(this, PriceMonitorService.class);
        intent.setAction(PriceMonitorService.ACTION_STOP);
        startService(intent);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    private void setRefreshing(boolean refreshing) {
        refreshProgress.setVisibility(refreshing ? View.VISIBLE : View.GONE);
        refreshButton.setEnabled(!refreshing);
    }

    private String conditionLabel(String condition) {
        if (GoldAlert.CONDITION_BELOW.equals(condition)) {
            return "下跌到或低于";
        }
        return "上涨到或高于";
    }

    private String unitLabel(String unit) {
        if (GoldAlert.UNIT_USD_OUNCE.equals(unit)) {
            return "美元/金衡盎司";
        }
        return "人民币/克";
    }

    private String formatValue(String unit, double value) {
        if (GoldAlert.UNIT_USD_OUNCE.equals(unit)) {
            return String.format(Locale.CHINA, "$%,.2f", value);
        }
        return String.format(Locale.CHINA, "¥%,.2f", value);
    }

    private String sourceLabel(String sourceName) {
        if (sourceName == null || sourceName.trim().isEmpty() || "Unknown".equals(sourceName)) {
            return "金价 API";
        }
        return sourceName;
    }

    private String readableError(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 80 ? message.substring(0, 80) : message;
    }
}
