package it.paolo.gpsautoclick;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {
    private EditText latInput, lonInput, buttonTextInput;
    private TextView gpsStatus, autoStatus, selectedAppName, selectedPackage;
    private SharedPreferences prefs;
    private String targetPackage = "";
    private String targetAppName = "";

    private static class AppEntry {
        final String label;
        final String packageName;
        AppEntry(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
        }
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

        latInput.setText(prefs.getString("lat", "45.4642"));
        lonInput.setText(prefs.getString("lon", "9.1900"));
        buttonTextInput.setText(prefs.getString("buttonText", ""));
        targetPackage = prefs.getString("targetPackage", "");
        targetAppName = prefs.getString("targetAppName", "");
        updateSelectedAppUi();

        Button startGpsBtn = findViewById(R.id.startGpsBtn);
        Button stopGpsBtn = findViewById(R.id.stopGpsBtn);
        Button mockSettingsBtn = findViewById(R.id.mockSettingsBtn);
        Button accessibilityBtn = findViewById(R.id.accessibilityBtn);
        Button launchAutoBtn = findViewById(R.id.launchAutoBtn);
        Button pickAppBtn = findViewById(R.id.pickAppBtn);

        startGpsBtn.setOnClickListener(v -> startMockGps());
        stopGpsBtn.setOnClickListener(v -> {
            stopService(new Intent(this, MockLocationService.class));
            gpsStatus.setText("GPS simulato: fermo");
        });
        mockSettingsBtn.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
        accessibilityBtn.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        pickAppBtn.setOnClickListener(v -> showInstalledApps());
        launchAutoBtn.setOnClickListener(v -> launchAndClick());
    }

    private void showInstalledApps() {
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

        Collections.sort(apps, Comparator.comparing(a -> a.label.toLowerCase()));

        if (apps.isEmpty()) {
            Toast.makeText(this, "Nessuna app trovata", Toast.LENGTH_LONG).show();
            return;
        }

        String[] labels = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            AppEntry app = apps.get(i);
            labels[i] = app.label + "\n" + app.packageName;
        }

        new AlertDialog.Builder(this)
                .setTitle("Scegli app da automatizzare")
                .setItems(labels, (dialog, which) -> {
                    AppEntry app = apps.get(which);
                    targetPackage = app.packageName;
                    targetAppName = app.label;
                    prefs.edit()
                            .putString("targetPackage", targetPackage)
                            .putString("targetAppName", targetAppName)
                            .apply();
                    updateSelectedAppUi();
                    autoStatus.setText("App selezionata: " + targetAppName);
                })
                .setNegativeButton("Annulla", null)
                .show();
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
        } catch (Exception e) {
            return pkg;
        }
    }

    private void startMockGps() {
        double lat, lon;
        try {
            lat = Double.parseDouble(latInput.getText().toString().trim().replace(',', '.'));
            lon = Double.parseDouble(lonInput.getText().toString().trim().replace(',', '.'));
        } catch (Exception e) {
            Toast.makeText(this, "Coordinate non valide", Toast.LENGTH_SHORT).show();
            return;
        }
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            Toast.makeText(this, "Coordinate fuori intervallo", Toast.LENGTH_SHORT).show();
            return;
        }

        prefs.edit().putString("lat", String.valueOf(lat)).putString("lon", String.valueOf(lon)).apply();
        requestPermissionsIfNeeded();

        Intent i = new Intent(this, MockLocationService.class);
        i.putExtra("lat", lat);
        i.putExtra("lon", lon);
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
        if (targetPackage.isEmpty()) {
            Toast.makeText(this, "Prima premi SCEGLI APP", Toast.LENGTH_LONG).show();
            return;
        }
        if (button.isEmpty()) {
            Toast.makeText(this, "Inserisci il testo del pulsante da premere", Toast.LENGTH_LONG).show();
            return;
        }

        prefs.edit()
                .putString("targetPackage", targetPackage)
                .putString("targetAppName", targetAppName)
                .putString("buttonText", button)
                .putBoolean("armed", true)
                .apply();

        Intent launch = getPackageManager().getLaunchIntentForPackage(targetPackage);
        if (launch == null) {
            autoStatus.setText("Impossibile aprire " + targetAppName);
            Toast.makeText(this, "L'app selezionata non espone una schermata avviabile", Toast.LENGTH_LONG).show();
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(launch);
        autoStatus.setText("Automazione attiva: cerco \"" + button + "\" in " + targetAppName);
    }

    private void requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 10);
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
        }
    }
}
