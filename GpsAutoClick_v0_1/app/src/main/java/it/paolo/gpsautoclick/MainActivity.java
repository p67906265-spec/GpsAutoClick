package it.paolo.gpsautoclick;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_PICK_APP = 100;
    private EditText latInput, lonInput, packageInput, buttonTextInput;
    private TextView gpsStatus, autoStatus;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("cfg", MODE_PRIVATE);
        latInput = findViewById(R.id.latInput);
        lonInput = findViewById(R.id.lonInput);
        packageInput = findViewById(R.id.packageInput);
        buttonTextInput = findViewById(R.id.buttonTextInput);
        gpsStatus = findViewById(R.id.gpsStatus);
        autoStatus = findViewById(R.id.autoStatus);

        latInput.setText(prefs.getString("lat", "45.4642"));
        lonInput.setText(prefs.getString("lon", "9.1900"));
        packageInput.setText(prefs.getString("targetPackage", ""));
        buttonTextInput.setText(prefs.getString("buttonText", ""));

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
        pickAppBtn.setOnClickListener(v -> pickApp());
        launchAutoBtn.setOnClickListener(v -> launchAndClick());
    }

    private void pickApp() {
        Intent base = new Intent(Intent.ACTION_MAIN);
        base.addCategory(Intent.CATEGORY_LAUNCHER);
        Intent picker = new Intent(Intent.ACTION_PICK_ACTIVITY);
        picker.putExtra(Intent.EXTRA_INTENT, base);
        picker.putExtra(Intent.EXTRA_TITLE, "Scegli app da automatizzare");
        startActivityForResult(picker, REQ_PICK_APP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_APP && resultCode == RESULT_OK && data != null && data.getComponent() != null) {
            String pkg = data.getComponent().getPackageName();
            packageInput.setText(pkg);
            prefs.edit().putString("targetPackage", pkg).apply();
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
        String pkg = packageInput.getText().toString().trim();
        String button = buttonTextInput.getText().toString().trim();
        if (pkg.isEmpty() || button.isEmpty()) {
            Toast.makeText(this, "Inserisci pacchetto app e testo del pulsante", Toast.LENGTH_LONG).show();
            return;
        }
        prefs.edit().putString("targetPackage", pkg).putString("buttonText", button).putBoolean("armed", true).apply();

        Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) {
            autoStatus.setText("App non trovata: controlla il nome pacchetto");
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(launch);
        autoStatus.setText("Automazione armata: cerco \"" + button + "\"");
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
