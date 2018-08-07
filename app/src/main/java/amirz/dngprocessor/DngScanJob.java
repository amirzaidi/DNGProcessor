package amirz.dngprocessor;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

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

            Log.e(TAG, "Scheduling job");
            js.schedule(sJobInfo);
        }
    }

    public DngScanJob() {
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        StringBuilder sb = new StringBuilder();
        sb.append("onStartJob: Media content has changed:\n");

        if (params.getTriggeredContentAuthorities() != null) {
            if (params.getTriggeredContentUris() != null) {
                for (Uri uri : params.getTriggeredContentUris()) {
                    String file = Path.getFileFromUri(this, uri);
                    if (file.endsWith(Path.EXT_RAW)) {
                        Log.e(TAG, "Starting processing of " + file);
                        new Thread(new DngParser(this, uri)).start();
                    }
                    sb.append(file);
                    sb.append(", ");
                }
            }
        }

        Log.e(TAG, sb.toString());
        scheduleJob(this);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.e(TAG, "onStopJob");
        return false;
    }
}
