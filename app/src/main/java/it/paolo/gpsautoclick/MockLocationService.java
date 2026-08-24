package it.paolo.gpsautoclick;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

public class MockLocationService extends Service {
    private static final String CHANNEL = "mock_location";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocationManager lm;
    private double lat, lon;
    private boolean providerAdded = false;

    private final Runnable updater = new Runnable() {
        @Override public void run() {
            pushLocation();
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        createChannel();
        Notification n = new Notification.Builder(this, CHANNEL)
                .setContentTitle("GPS AutoClick")
                .setContentText("Posizione GPS simulata attiva")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .build();
        startForeground(42, n);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            lat = intent.getDoubleExtra("lat", 0.0);
            lon = intent.getDoubleExtra("lon", 0.0);
        }
        handler.removeCallbacks(updater);
        try {
            ensureProvider();
            updater.run();
        } catch (SecurityException e) {
            stopSelf();
        }
        return START_STICKY;
    }

    @SuppressWarnings("deprecation")
    private void ensureProvider() {
        try {
            lm.addTestProvider(LocationManager.GPS_PROVIDER,
                    false, false, false, false,
                    true, true, true,
                    1, 1);
            providerAdded = true;
        } catch (IllegalArgumentException ignored) {
            providerAdded = true;
        }
        lm.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
    }

    private void pushLocation() {
        try {
            Location loc = new Location(LocationManager.GPS_PROVIDER);
            loc.setLatitude(lat);
            loc.setLongitude(lon);
            loc.setAltitude(10.0);
            loc.setAccuracy(3.0f);
            loc.setSpeed(0.0f);
            loc.setBearing(0.0f);
            loc.setTime(System.currentTimeMillis());
            loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            lm.setTestProviderLocation(LocationManager.GPS_PROVIDER, loc);
        } catch (Exception e) {
            stopSelf();
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Posizione simulata", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(updater);
        if (lm != null && providerAdded) {
            try { lm.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false); } catch (Exception ignored) {}
            try { lm.removeTestProvider(LocationManager.GPS_PROVIDER); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
