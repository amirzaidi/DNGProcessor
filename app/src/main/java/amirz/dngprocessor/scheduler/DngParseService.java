package amirz.dngprocessor.scheduler;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.File;

import amirz.dngprocessor.NotifHandler;
import amirz.dngprocessor.Path;
import amirz.dngprocessor.parser.DngParser;

public class DngParseService extends IntentService {
    private static final String TAG = "DngParseService";
    private static final boolean ATLEAST_OREO = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;

    public static void runForUri(Context context, Uri uri) {
        context = context.getApplicationContext();

        Intent intent = new Intent(context, DngParseService.class);
        intent.setData(uri);

        if (ATLEAST_OREO) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public DngParseService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        Uri uri = intent.getData();
        String file = Path.getFileFromUri(this, uri);
        Log.e(TAG, "onHandleIntent " + file);

        NotifHandler.create(this, file);
        if (!new File(Path.processedFile(file)).exists()) {
            new DngParser(this, uri).run();
        }
        NotifHandler.done(this);
    }
}
