package amirz.dngprocessor;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    private static final int REQUEST_PERMISSIONS = 1;
    private static final int REQUEST_IMAGE = 2;

    public static String VS;
    public static String PS;

    public String readRaw(int resId) {
        try (InputStream inputStream = getResources().openRawResource(resId)) {
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

            StringBuilder text = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }

            return text.toString();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        VS = readRaw(R.raw.stage1_vs);
        PS = readRaw(R.raw.stage1_fs);

        NotifHandler.createChannel(this);
        tryRequestImage();
    }

    private boolean hasPermissions() {
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void tryRequestImage() {
        if (hasPermissions()) {
            DngScanJob.scheduleJob(this);

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
                    new Thread(new DngParser(this, data.getData())).start();
                    break;
            }
        }
    }
}
