package com.ru.cameraimu;

import android.hardware.Camera;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;

public class CamCallbacks {

  private static long mLastTimestampMillis;

  static { mLastTimestampMillis = -1; }

  public static class ShutterCallback implements Camera.ShutterCallback {
    @Override
    public void onShutter() {
      mLastTimestampMillis = System.currentTimeMillis();
    }
  }

  public static class PictureCallback implements Camera.PictureCallback {
    private MainActivity mActivity;

    private final String TAG = "TAG/CameraIMU";

    public PictureCallback(MainActivity activity) { mActivity = activity; }

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
      long timestampMillis = mLastTimestampMillis;
      if (mActivity.NEED_RECORD && mActivity.isCapturing())
        recordPicture(data, camera, timestampMillis);

      // Take pictures continuously
      camera.startPreview();
      if (mActivity.isCapturing())
        camera.takePicture(mActivity.getShutter(), null, mActivity.getPicture());
    }

    private void recordPicture(byte[] data, Camera camera, long timestampMillis) {
      String filename = String.format(Locale.US, "%013d.jpg", timestampMillis);
      File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                           mActivity.mStorageDir + File.separator + "IMG" + File.separator + filename);

      // Write file
      try {
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(data);
        fos.close();
      } catch (IOException e) {
        Log.e(TAG, "recordPicture: " + e.getMessage());
      }
    }
  }
}
