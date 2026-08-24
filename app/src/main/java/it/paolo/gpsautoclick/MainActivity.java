package it.paolo.gpsautoclick;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private EditText latInput, lonInput, buttonTextInput;
    private TextView gpsStatus, autoStatus, selectedAppName, selectedPackage, scheduleStatus;
    private LinearLayout automationContainer;
    private SharedPreferences prefs;
    private String targetPackage = "";
    private String targetAppName = "";
    private final AutomationView[] autoViews = new AutomationView[4];

    private static class AppEntry {
        final String label;
        final String packageName;
        AppEntry(String label, String packageName) { this.label = label; this.packageName = packageName; }
    }

    private static class AutomationView {
        CheckBox enabled;
        Button timeButton;
        Button appButton;
        TextView appLabel;
        EditText buttonInput;
        EditText latInput;
        EditText lonInput;
        int hour;
        int minute;
        String packageName = "";
        String appName = "";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("cfg", MODE_PRIVATE);
        latInput = findViewById(R.id.latInput);
        lonInput = findViewById(R.id.lonInput);
        buttonTextInput = findViewById(R.id.buttonTextInput);
        gpsStatus = findViewById(R.id.gpsStatus);
        autoStatus = findViewById(R.id.autoStatus);
        selectedAppName = findViewById(R.id.selectedAppName);
        selectedPackage = findViewById(R.id.selectedPackage);
        automationContainer = findViewById(R.id.automationContainer);
        scheduleStatus = findViewById(R.id.scheduleStatus);

        latInput.setText(prefs.getString("lat", "45.4642"));
        lonInput.setText(prefs.getString("lon", "9.1900"));
        buttonTextInput.setText(prefs.getString("buttonText", ""));
        targetPackage = prefs.getString("targetPackage", "");
        targetAppName = prefs.getString("targetAppName", "");
        updateSelectedAppUi();

        findViewById(R.id.startGpsBtn).setOnClickListener(v -> startMockGps());
        findViewById(R.id.stopGpsBtn).setOnClickListener(v -> {
            stopService(new Intent(this, MockLocationService.class));
            gpsStatus.setText("GPS simulato: fermo");
        });
        findViewById(R.id.mockSettingsBtn).setOnClickListener(v -> openDeveloperSettings());
        findViewById(R.id.accessibilityBtn).setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        findViewById(R.id.pickAppBtn).setOnClickListener(v -> showInstalledAppsForManual());
        findViewById(R.id.launchAutoBtn).setOnClickListener(v -> launchAndClick());
        findViewById(R.id.exactAlarmBtn).setOnClickListener(v -> requestExactAlarmPermission());
        findViewById(R.id.saveSchedulesBtn).setOnClickListener(v -> saveAndScheduleAll());

        buildAutomationPanels();
    }

    @Override
    protected void onResume() {
        super.onResume();
        String s = prefs.getString("lastAutoStatus", "");
        if (!s.isEmpty()) autoStatus.setText(s);
    }

    private void buildAutomationPanels() {
        automationContainer.removeAllViews();
        for (int slot = 1; slot <= 4; slot++) {
            AutomationView av = new AutomationView();
            autoViews[slot - 1] = av;

            LinearLayout panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(14), dp(12), dp(14), dp(12));
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            pp.topMargin = dp(12);
            panel.setLayoutParams(pp);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(getColor(R.color.panel));
            bg.setCornerRadius(dp(14));
            panel.setBackground(bg);

            av.enabled = new CheckBox(this);
            av.enabled.setText("Automazione " + slot);
            av.enabled.setTextColor(getColor(R.color.white));
            av.enabled.setTextSize(17);
            panel.addView(av.enabled);

            av.hour = prefs.getInt("auto_" + slot + "_hour", defaultHour(slot));
            av.minute = prefs.getInt("auto_" + slot + "_minute", 0);
            av.enabled.setChecked(prefs.getBoolean("auto_" + slot + "_enabled", false));

            av.timeButton = new Button(this);
            av.timeButton.setText("Ora click: " + timeText(av.hour, av.minute));
            av.timeButton.setOnClickListener(v -> chooseTime(av));
            panel.addView(av.timeButton, fullButtonParams());

            av.packageName = prefs.getString("auto_" + slot + "_package", targetPackage);
            av.appName = prefs.getString("auto_" + slot + "_appName", targetAppName);

            av.appButton = new Button(this);
            av.appButton.setText("Scegli app");
            final int chosenSlot = slot;
            av.appButton.setOnClickListener(v -> showInstalledAppsForSlot(chosenSlot));
            panel.addView(av.appButton, fullButtonParams());

            av.appLabel = new TextView(this);
            av.appLabel.setTextColor(getColor(R.color.muted));
            av.appLabel.setTextSize(13);
            av.appLabel.setPadding(0, dp(5), 0, 0);
            panel.addView(av.appLabel);
            updateSlotAppLabel(av);

            av.buttonInput = makeEdit("Tasto da premere, es. ENTRATA / USCITA");
            av.buttonInput.setText(prefs.getString("auto_" + slot + "_button", ""));
            panel.addView(av.buttonInput);

            LinearLayout coords = new LinearLayout(this);
            coords.setOrientation(LinearLayout.HORIZONTAL);
            av.latInput = makeEdit("Latitudine");
            av.lonInput = makeEdit("Longitudine");
            av.latInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
            av.lonInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
            av.latInput.setText(prefs.getString("auto_" + slot + "_lat", prefs.getString("lat", "45.4642")));
            av.lonInput.setText(prefs.getString("auto_" + slot + "_lon", prefs.getString("lon", "9.1900")));
            LinearLayout.LayoutParams half1 = new LinearLayout.LayoutParams(0, dp(54), 1f);
            half1.setMarginEnd(dp(5));
            LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, dp(54), 1f);
            half2.setMarginStart(dp(5));
            coords.addView(av.latInput, half1);
            coords.addView(av.lonInput, half2);
            panel.addView(coords);

            automationContainer.addView(panel);
        }
    }

    private EditText makeEdit(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(getColor(R.color.muted));
        e.setTextColor(getColor(R.color.white));
        e.setSingleLine(true);
        e.setTextSize(15);
        e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.cyan)));
        e.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)));
        return e;
    }

    private LinearLayout.LayoutParams fullButtonParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        lp.topMargin = dp(5);
        return lp;
    }

    private int defaultHour(int slot) {
        if (slot == 1) return 7;
        if (slot == 2) return 12;
        if (slot == 3) return 13;
        return 17;
    }

    private void chooseTime(AutomationView av) {
        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            av.hour = hourOfDay;
            av.minute = minute;
            av.timeButton.setText("Ora click: " + timeText(hourOfDay, minute));
        }, av.hour, av.minute, true).show();
    }

    private String timeText(int h, int m) {
        return String.format(Locale.ITALY, "%02d:%02d", h, m);
    }

    private void saveAndScheduleAll() {
        SharedPreferences.Editor ed = prefs.edit();
        int enabledCount = 0;
        for (int i = 0; i < 4; i++) {
            int slot = i + 1;
            AutomationView av = autoViews[i];
            String button = av.buttonInput.getText().toString().trim();
            String lat = av.latInput.getText().toString().trim().replace(',', '.');
            String lon = av.lonInput.getText().toString().trim().replace(',', '.');

            if (av.enabled.isChecked()) {
                if (av.packageName.isEmpty()) {
                    Toast.makeText(this, "Automazione " + slot + ": scegli l'app", Toast.LENGTH_LONG).show();
                    return;
                }
                if (button.isEmpty()) {
                    Toast.makeText(this, "Automazione " + slot + ": inserisci il tasto", Toast.LENGTH_LONG).show();
                    return;
                }
                if (!validCoordinates(lat, lon)) {
                    Toast.makeText(this, "Automazione " + slot + ": coordinate non valide", Toast.LENGTH_LONG).show();
                    return;
                }
                enabledCount++;
            }

            ed.putBoolean("auto_" + slot + "_enabled", av.enabled.isChecked());
            ed.putInt("auto_" + slot + "_hour", av.hour);
            ed.putInt("auto_" + slot + "_minute", av.minute);
            ed.putString("auto_" + slot + "_package", av.packageName);
            ed.putString("auto_" + slot + "_appName", av.appName);
            ed.putString("auto_" + slot + "_button", button);
            ed.putString("auto_" + slot + "_lat", lat);
            ed.putString("auto_" + slot + "_lon", lon);
        }
        ed.apply();
        AlarmScheduler.scheduleAll(this);
        scheduleStatus.setText("Programmazione salvata: " + enabledCount + " automazioni attive");
        Toast.makeText(this, "Orari salvati e programmati", Toast.LENGTH_SHORT).show();
    }

    private boolean validCoordinates(String latText, String lonText) {
        try {
            double lat = Double.parseDouble(latText);
            double lon = Double.parseDouble(lonText);
            return lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
        } catch (Exception e) { return false; }
    }

    private void requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (am != null && am.canScheduleExactAlarms()) {
                Toast.makeText(this, "Orari precisi già consentiti", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()));
                startActivity(i);
                return;
            } catch (Exception ignored) { }
        }
        Toast.makeText(this, "Nessuna autorizzazione aggiuntiva necessaria", Toast.LENGTH_SHORT).show();
    }

    private void showInstalledAppsForManual() {
        showInstalledApps(app -> {
            targetPackage = app.packageName;
            targetAppName = app.label;
            prefs.edit().putString("targetPackage", targetPackage).putString("targetAppName", targetAppName).apply();
            updateSelectedAppUi();
            autoStatus.setText("App selezionata: " + targetAppName);
        });
    }

    private void showInstalledAppsForSlot(int slot) {
        showInstalledApps(app -> {
            AutomationView av = autoViews[slot - 1];
            av.packageName = app.packageName;
            av.appName = app.label;
            updateSlotAppLabel(av);
        });
    }

    private interface AppChoice { void onChosen(AppEntry app); }

    private void showInstalledApps(AppChoice choice) {
        PackageManager pm = getPackageManager();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL);
        List<AppEntry> apps = new ArrayList<>();
        for (ResolveInfo info : resolved) {
            if (info.activityInfo == null || info.activityInfo.packageName == null) continue;
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            CharSequence labelCs = info.loadLabel(pm);
            String label = labelCs != null ? labelCs.toString() : pkg;
            apps.add(new AppEntry(label, pkg));
        }
        Collections.sort(apps, Comparator.comparing(a -> a.label.toLowerCase(Locale.ROOT)));
        if (apps.isEmpty()) {
            Toast.makeText(this, "Nessuna app trovata", Toast.LENGTH_LONG).show();
            return;
        }
        String[] labels = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) labels[i] = apps.get(i).label + "\n" + apps.get(i).packageName;
        new AlertDialog.Builder(this)
                .setTitle("Scegli app da automatizzare")
                .setItems(labels, (dialog, which) -> choice.onChosen(apps.get(which)))
                .setNegativeButton("Annulla", null)
                .show();
    }

    private void updateSlotAppLabel(AutomationView av) {
        if (av.packageName.isEmpty()) av.appLabel.setText("App: nessuna");
        else {
            if (av.appName.isEmpty()) av.appName = getLabelForPackage(av.packageName);
            av.appLabel.setText("App: " + av.appName + "\n" + av.packageName);
        }
    }

    private void updateSelectedAppUi() {
        if (targetPackage.isEmpty()) {
            selectedAppName.setText("Nessuna app selezionata");
            selectedPackage.setText("Pacchetto: —");
        } else {
            if (targetAppName.isEmpty()) targetAppName = getLabelForPackage(targetPackage);
            selectedAppName.setText("App scelta: " + targetAppName);
            selectedPackage.setText("Pacchetto: " + targetPackage);
        }
    }

    private String getLabelForPackage(String pkg) {
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(pkg, 0);
            CharSequence label = getPackageManager().getApplicationLabel(ai);
            return label != null ? label.toString() : pkg;
        } catch (Exception e) { return pkg; }
    }

    private void openDeveloperSettings() {
        try { startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)); }
        catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
    }

    private void startMockGps() {
        double lat, lon;
        try {
            lat = Double.parseDouble(latInput.getText().toString().trim().replace(',', '.'));
            lon = Double.parseDouble(lonInput.getText().toString().trim().replace(',', '.'));
        } catch (Exception e) {
            Toast.makeText(this, "Coordinate non valide", Toast.LENGTH_SHORT).show(); return;
        }
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            Toast.makeText(this, "Coordinate fuori intervallo", Toast.LENGTH_SHORT).show(); return;
        }
        prefs.edit().putString("lat", String.valueOf(lat)).putString("lon", String.valueOf(lon)).apply();
        requestPermissionsIfNeeded();
        Intent i = new Intent(this, MockLocationService.class);
        i.putExtra("lat", lat); i.putExtra("lon", lon);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            gpsStatus.setText("GPS simulato: " + lat + ", " + lon);
        } catch (Exception e) {
            gpsStatus.setText("Errore GPS: abilita questa app come posizione fittizia");
            Toast.makeText(this, "Abilita GPS AutoClick come app posizione fittizia", Toast.LENGTH_LONG).show();
        }
    }

    private void launchAndClick() {
        String button = buttonTextInput.getText().toString().trim();
        if (targetPackage.isEmpty()) { Toast.makeText(this, "Prima premi SCEGLI APP", Toast.LENGTH_LONG).show(); return; }
        if (button.isEmpty()) { Toast.makeText(this, "Inserisci il testo del pulsante da premere", Toast.LENGTH_LONG).show(); return; }
        prefs.edit().putString("targetPackage", targetPackage).putString("targetAppName", targetAppName)
                .putString("buttonText", button).putBoolean("armed", true).apply();
        Intent launch = getPackageManager().getLaunchIntentForPackage(targetPackage);
        if (launch == null) { autoStatus.setText("Impossibile aprire " + targetAppName); return; }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(launch);
        autoStatus.setText("Automazione attiva: cerco \"" + button + "\"");
    }

    private void requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 10);
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
        }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
