package com.ru.cameraimu;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.AsyncTask;
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
        camera.takePicture(mActivity.getShutterCallback(), null, mActivity.getPictureCallback());
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

  public static class PreviewCallback implements Camera.PreviewCallback {
    private MainActivity mActivity;
    private float mCurrentFPS = 0f;
    private int mLocalFrameCount = 0;

    private final String TAG = "TAG/CameraIMU";

    public PreviewCallback(MainActivity activity) { mActivity = activity; }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
      final long timestampMillis = System.currentTimeMillis();
      final int frameW = camera.getParameters().getPreviewSize().width;
      final int frameH = camera.getParameters().getPreviewSize().height;
      if (mActivity.NEED_RECORD && mActivity.isCapturing()) {
        // Instantiate an AsyncTask to do the compressing and saving jobs
        // in order to prevent blocking
        new AsyncTask<byte[], Void, Void>() {
          @Override
          protected Void doInBackground(byte[]... params) {
            byte[] data = params[0];
            compressAndSaveAsJPEG(data, frameW, frameH, timestampMillis);
            return null;
          }
        }.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, data);
      }

      if (mLastTimestampMillis == -1) {
        mCurrentFPS = 0f;
        mLastTimestampMillis = timestampMillis;
      } else {
        ++mLocalFrameCount;
        if (mLocalFrameCount == MainActivity.INFO_VIEW_UPDATE_RATE) {
          mCurrentFPS = MainActivity.INFO_VIEW_UPDATE_RATE * 1000f / (timestampMillis - mLastTimestampMillis);
          mLastTimestampMillis = timestampMillis;
          mLocalFrameCount = 0;
        }
      }
    }

    private void compressAndSaveAsJPEG(byte[] data, int w, int h, long timestampMillis) {
      // Note: The default original data is in NV21 format
      YuvImage img = new YuvImage(data, ImageFormat.NV21, w, h, null);
      String filename = String.format(Locale.US, "%013d.jpg", timestampMillis);
      File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                           mActivity.mStorageDir + File.separator + "IMG" + File.separator + filename);

      try {
        FileOutputStream fos = new FileOutputStream(file);
        if (!img.compressToJpeg(new Rect(0, 0, w, h), 95, fos))
          Log.e(TAG, "compressAndSaveAsJPEG: Failed to compress image!");
        fos.close();
      } catch (IOException e) {
        Log.e(TAG, "recordPicture: " + e.getMessage());
      }
    }

    public float getCurrentFPS() { return mCurrentFPS; }
  }
}
