package amirz.dngprocessor.scheduler;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.File;

import amirz.dngprocessor.NotifHandler;
import amirz.dngprocessor.Path;
import amirz.dngprocessor.parser.DngParser;

public class DngParseService extends IntentService {
    private static final String TAG = "DngParseService";

    public static void runForUri(Context context, Uri uri) {
        context = context.getApplicationContext();

        Intent intent = new Intent(context, DngParseService.class);
        intent.setData(uri);
        context.startService(intent);
    }

    public DngParseService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        Uri uri = intent.getData();
        String file = Path.getFileFromUri(this, uri);
        Log.e(TAG, "onHandleIntent " + file);

        if (!new File(Path.processedFile(file)).exists()) {
            NotifHandler.create(this, file);
            new DngParser(this, uri).run();
            NotifHandler.done(this);
        }
    }
}
