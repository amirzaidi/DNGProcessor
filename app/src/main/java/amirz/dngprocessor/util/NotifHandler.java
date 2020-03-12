package amirz.dngprocessor.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import amirz.dngprocessor.R;

import static amirz.dngprocessor.util.Utilities.ATLEAST_OREO;

public class NotifHandler {
    private static final String TAG = "NotifHandler";
    private static final String CHANNEL = "default";
    private static final int FOREGROUND_ID = 1;
    private static Notification.Builder mBuilder;

    public static void createChannel(Context context) {
        if (ATLEAST_OREO) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Default",
                    NotificationManager.IMPORTANCE_LOW);
            channel.enableLights(false);
            channel.enableVibration(false);
            manager(context).createNotificationChannel(channel);
        }
    }

    public static void create(Service service, String name) {
        PendingIntent pendingIntent = PendingIntent.getActivity(service, 0, new Intent(), 0);
        if (ATLEAST_OREO) {
            mBuilder = new Notification.Builder(service, CHANNEL);
        } else {
            mBuilder = new Notification.Builder(service);
        }

        mBuilder.setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Processing " + name)
                .setContentIntent(pendingIntent);

        service.startForeground(FOREGROUND_ID, mBuilder.build());
    }

    public static void progress(Context context, int max, int progress) {
        Notification notif = mBuilder.setProgress(max, progress, false).build();
        manager(context).notify(FOREGROUND_ID, notif);
        Log.w(TAG, "Raw conversion " + progress + "/" + max);
    }

    public static void done(Service service) {
        service.stopForeground(true);
    }

    private static NotificationManager manager(Context context) {
        return context.getSystemService(NotificationManager.class);
    }
}
