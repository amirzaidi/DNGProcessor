package amirz.dngprocessor.scheduler;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import amirz.dngprocessor.Path;
import amirz.dngprocessor.Utilities;

public class DngScanJob extends JobService {
    private static final String TAG = "DngScanJob";
    private static final int SCAN_DNG_JOB = 9500;

    public static final JobInfo.TriggerContentUri TRIGGER_CONTENT_URI =
            new JobInfo.TriggerContentUri(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS);

    private static JobInfo sJobInfo;

    public static void scheduleJob(Context context) {
        JobScheduler js = context.getSystemService(JobScheduler.class);
        if (js != null) {
            if (sJobInfo == null) {
                sJobInfo = new JobInfo.Builder(SCAN_DNG_JOB,
                        new ComponentName(context.getApplicationContext(), DngScanJob.class))
                        .addTriggerContentUri(DngScanJob.TRIGGER_CONTENT_URI)
                        .setTriggerContentMaxDelay(1)
                        .build();
            }

            Log.w(TAG, "Scheduling job");
            js.schedule(sJobInfo);
        }
    }

    public DngScanJob() {
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        StringBuilder sb = new StringBuilder();
        sb.append("onStartJob: Media content has changed: ");

        ContentResolver contentResolver = getContentResolver();
        SharedPreferences prefs = Utilities.prefs(this);

        if (params.getTriggeredContentAuthorities() != null) {
            if (params.getTriggeredContentUris() != null) {
                for (Uri uri : params.getTriggeredContentUris()) {
                    // If this is an unprocessed RAW image, process it and save that we did.
                    if (Path.MIME_RAW.equals(contentResolver.getType(uri))
                            && prefs.getBoolean(uri.toString(), true)) {
                        prefs.edit().putBoolean(uri.toString(), false).apply();
                        DngParseService.runForUri(this, uri);

                        sb.append("PROCESS@");
                    }

                    try {
                        String file = Path.getFileFromUri(this, uri);
                        sb.append(file);
                        sb.append(", ");
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        Log.w(TAG, sb.toString());
        scheduleJob(this);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.w(TAG, "onStopJob");
        return false;
    }
}
