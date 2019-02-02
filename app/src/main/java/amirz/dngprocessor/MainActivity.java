package amirz.dngprocessor;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

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
        setContentView(R.layout.activity_main);

        NotifHandler.createChannel(this);
        tryRequestImage();
    }

    private boolean hasPermissions() {
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PERMISSION_GRANTED;
    }

    private void tryRequestImage() {
        if (hasPermissions()) {
            DngScanJob.scheduleJob(this);

            Intent picker = new Intent(Intent.ACTION_GET_CONTENT);
            picker.setType(Path.MIME_RAW);
            startActivityForResult(picker, REQUEST_IMAGE);
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
                    tryRequestImage();
                }
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_IMAGE:
                if (resultCode == RESULT_OK) {
                    DngParseService.runForUri(this, data.getData());
                }
                break;
        }
    }
}
