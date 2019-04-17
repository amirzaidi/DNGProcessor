package amirz.dngprocessor;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;

import amirz.dngprocessor.scheduler.DngParseService;
import amirz.dngprocessor.scheduler.DngScanJob;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    private static final int REQUEST_PERMISSIONS = 1;
    private static final int REQUEST_IMAGE = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            NotifHandler.createChannel(this);
            tryLoad();
        }
    }

    private boolean hasPermissions() {
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PERMISSION_GRANTED;
    }

    private void tryLoad() {
        if (hasPermissions()) {
            DngScanJob.scheduleJob(this);
            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new Preferences.Fragment())
                    .commit();
        } else {
            requestPermissions(new String[] {
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case REQUEST_PERMISSIONS:
                if (grantResults[0] == PERMISSION_GRANTED) {
                    tryLoad();
                }
                break;
        }
    }

    public boolean requestImage(Preference preference) {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        picker.setType(Path.MIME_RAW);
        picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(picker, REQUEST_IMAGE);

        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE && resultCode == RESULT_OK) {
            int flags = data.getFlags();
            ClipData cd = data.getClipData();
            if (cd == null) {
                process(data.getData(), flags);
            } else {
                for (int i = 0; i < cd.getItemCount(); i++) {
                    process(cd.getItemAt(i).getUri(), flags);
                }
            }
        }
    }

    @Override
    public void recreate() {
        finish();
        startActivity(getIntent());
    }

    private void process(Uri uri, int flags) {
        getContentResolver().takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION);
        DngParseService.runForUri(this, uri);
    }
}
