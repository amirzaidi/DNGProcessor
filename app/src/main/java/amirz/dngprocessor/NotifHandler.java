package amirz.dngprocessor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.SparseArray;

public class NotifHandler {
    private static final String CHANNEL = "default";
    private static SparseArray<Notification.Builder> sMap = new SparseArray<>();

    public static void createChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Default",
                NotificationManager.IMPORTANCE_LOW);
        channel.enableLights(false);
        channel.enableVibration(false);
        manager(context).createNotificationChannel(channel);
    }

    public static void create(Context context, String name) {
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, new Intent(), 0);
        sMap.append(name.hashCode(), new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Processing " + name)
                .setContentIntent(pendingIntent));
    }

    public static void progress(Context context, String name, int max, int progress) {
        Notification notif = sMap.get(name.hashCode())
                .setProgress(max, progress, false)
                .build();
        notif.flags |= Notification.FLAG_ONGOING_EVENT;
        manager(context).notify(name.hashCode(), notif);
    }

    public static void done(Context context, String name) {
        manager(context).cancel(name.hashCode());
    }

    private static NotificationManager manager(Context context) {
        return context.getSystemService(NotificationManager.class);
    }
}
