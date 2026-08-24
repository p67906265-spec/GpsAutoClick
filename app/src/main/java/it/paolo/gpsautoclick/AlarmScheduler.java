package it.paolo.gpsautoclick;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

public final class AlarmScheduler {
    private AlarmScheduler() {}

    public static void scheduleSlot(Context context, int slot) {
        android.content.SharedPreferences p = context.getSharedPreferences("cfg", Context.MODE_PRIVATE);
        cancelSlot(context, slot);
        if (!p.getBoolean("auto_" + slot + "_enabled", false)) return;

        int hour = p.getInt("auto_" + slot + "_hour", 7);
        int minute = p.getInt("auto_" + slot + "_minute", 0);

        Calendar when = Calendar.getInstance();
        when.set(Calendar.HOUR_OF_DAY, hour);
        when.set(Calendar.MINUTE, minute);
        when.set(Calendar.SECOND, 0);
        when.set(Calendar.MILLISECOND, 0);
        if (when.getTimeInMillis() <= System.currentTimeMillis()) {
            when.add(Calendar.DAY_OF_YEAR, 1);
        }

        Intent i = new Intent(context, AlarmReceiver.class);
        i.putExtra("slot", slot);
        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                5000 + slot,
                i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        long trigger = when.getTimeInMillis();
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
        } else if (Build.VERSION.SDK_INT >= 23) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, trigger, pi);
        }
    }

    public static void scheduleAll(Context context) {
        for (int i = 1; i <= 4; i++) scheduleSlot(context, i);
    }

    public static void cancelSlot(Context context, int slot) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(context, AlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                5000 + slot,
                i,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        if (pi != null) {
            am.cancel(pi);
            pi.cancel();
        }
    }
}
