package it.paolo.gpsautoclick;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int slot = intent != null ? intent.getIntExtra("slot", 0) : 0;
        if (slot < 1 || slot > 4) return;

        SharedPreferences p = context.getSharedPreferences("cfg", Context.MODE_PRIVATE);
        if (!p.getBoolean("auto_" + slot + "_enabled", false)) return;

        String pkg = p.getString("auto_" + slot + "_package", "");
        String appName = p.getString("auto_" + slot + "_appName", pkg);
        String button = p.getString("auto_" + slot + "_button", "");
        double lat = parse(p.getString("auto_" + slot + "_lat", p.getString("lat", "45.4642")), 45.4642);
        double lon = parse(p.getString("auto_" + slot + "_lon", p.getString("lon", "9.1900")), 9.1900);

        p.edit()
                .putString("targetPackage", pkg)
                .putString("targetAppName", appName)
                .putString("buttonText", button)
                .putBoolean("armed", true)
                .putString("lastAutoStatus", "Automazione " + slot + " avviata")
                .apply();

        Intent gps = new Intent(context, MockLocationService.class);
        gps.putExtra("lat", lat);
        gps.putExtra("lon", lon);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(gps);
            else context.startService(gps);
        } catch (Exception ignored) { }

        try {
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                context.startActivity(launch);
            } else {
                p.edit().putString("lastAutoStatus", "Automazione " + slot + ": app non avviabile").apply();
            }
        } catch (Exception e) {
            p.edit().putString("lastAutoStatus", "Automazione " + slot + ": Android ha bloccato l'avvio automatico").apply();
        }

        AlarmScheduler.scheduleSlot(context, slot);
    }

    private double parse(String value, double fallback) {
        try { return Double.parseDouble(value.replace(',', '.')); }
        catch (Exception e) { return fallback; }
    }
}
