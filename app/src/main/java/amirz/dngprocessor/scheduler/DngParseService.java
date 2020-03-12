package amirz.dngprocessor.scheduler;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import java.io.File;

import amirz.dngprocessor.util.NotifHandler;
import amirz.dngprocessor.util.Path;
import amirz.dngprocessor.Preferences;
import amirz.dngprocessor.R;
import amirz.dngprocessor.util.Utilities;
import amirz.dngprocessor.parser.DngParser;

import static amirz.dngprocessor.util.Utilities.ATLEAST_OREO;

public class DngParseService extends IntentService {
    private static final String TAG = "DngParseService";

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
        try {
            Preferences pref = Preferences.global();
            pref.applyAll(Utilities.prefs(this), getResources());
            Utilities.prefs(this)
                    .edit()
                    .putString(getString(R.string.pref_reprocess), uri.toString())
                    .apply();
            new DngParser(this, uri).run();
            if (pref.deleteOriginal.get()) {
                String path = Path.getPathFromUri(this, uri);
                Log.e(TAG, "Deleting " + path);
                File resolvedFile = new File(path);
                if (resolvedFile.delete()) {
                    sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                            Uri.fromFile(resolvedFile)));
                } else {
                    new Handler(getMainLooper()).post(() ->
                            Toast.makeText(this, "Could not delete " + file,
                                    Toast.LENGTH_SHORT).show());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            new Handler(getMainLooper()).post(() ->
                    Toast.makeText(this, "DNG Processor could not load " + file,
                            Toast.LENGTH_SHORT).show());
        }
        NotifHandler.done(this);
    }
}
