package amirz.dngprocessor;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    private static final int REQUEST_PERMISSIONS = 1;
    private static final int REQUEST_IMAGE = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tryRequestImage();
    }

    private boolean hasPermissions() {
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void tryRequestImage() {
        if (hasPermissions()) {
            Intent picker = new Intent(Intent.ACTION_GET_CONTENT);
            picker.setType("image/x-adobe-dng");
            startActivityForResult(picker, REQUEST_IMAGE);
        } else {
            requestPermissions(new String[] {
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_PERMISSIONS);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_PERMISSIONS:
                    tryRequestImage();
                    break;
                case REQUEST_IMAGE:
                    new Thread(new Parser(this, data.getData())).start();
                    break;
            }
        }
    }
}
