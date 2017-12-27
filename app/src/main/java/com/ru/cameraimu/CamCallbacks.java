package com.ru.cameraimu;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.FrameLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;

class CamCallbacks {
  private static final String TAG = "TAG/CameraIMU";

  private static class SavePictureAyncTask extends AsyncTask<byte[], Void, Void> {
    private String mPrefix;
    private int mFrameW, mFrameH;
    private long mTimestampNanos;

    private SavePictureAyncTask(String prefix, int frameW, int frameH, long timestampNanos) {
      mPrefix = prefix;
      mFrameW = frameW;
      mFrameH = frameH;
      mTimestampNanos = timestampNanos;
    }

    @Override
    protected Void doInBackground(byte[]... params) {
      byte[] data = params[0];
      compressAndSaveAsJPEG(data, mFrameW, mFrameH, mTimestampNanos);
      return null;
    }

    private void compressAndSaveAsJPEG(byte[] data, int w, int h, long timestampMillis) {
      // Note: The default original data is in NV21 format
      YuvImage img = new YuvImage(data, ImageFormat.NV21, w, h, null);
      String filename = String.format(Locale.US, "%013d.jpg", timestampMillis);
      File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                           mPrefix + File.separator + "IMG" + File.separator + filename);

      try {
        FileOutputStream fos = new FileOutputStream(file);
        if (!img.compressToJpeg(new Rect(0, 0, w, h), 95, fos))
          Log.e(TAG, "compressAndSaveAsJPEG: Failed to compress image!");
        fos.close();
      } catch (IOException e) {
        Log.e(TAG, "recordPicture: " + e.getMessage());
      }
    }
  }

  static class PreviewCallback implements Camera.PreviewCallback {
    private MainActivity mActivity;
    private float mCurrentFPS = 0f;
    private long mLastTimestampNanos = -1;
    private int mLocalFrameCount = 0;

    PreviewCallback(MainActivity activity) {
      mActivity = activity;
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
      long timestampNanos = System.nanoTime();
      if (mActivity.isCapturing()) {
        // Instantiate an AsyncTask to do the compressing and saving jobs
        // in order to prevent blocking
        new SavePictureAyncTask(
            mActivity.getStorageDir(),
            camera.getParameters().getPreviewSize().width,
            camera.getParameters().getPreviewSize().height,
            timestampNanos
        ).execute(data);
      }

      if (mLastTimestampNanos == -1) {
        mCurrentFPS = 0;
        mLastTimestampNanos = timestampNanos;
      } else {
        ++mLocalFrameCount;
        if (mLocalFrameCount == MainActivity.INFO_VIEW_UPDATE_RATE) {
          mCurrentFPS = MainActivity.INFO_VIEW_UPDATE_RATE * 1e9f / (timestampNanos - mLastTimestampNanos);
          mLastTimestampNanos = timestampNanos;
          mLocalFrameCount = 0;
        }
      }
    }

    void reset() {
      mLastTimestampNanos = -1;
    }

    float getCurrentFPS() {
      return mCurrentFPS;
    }
  }

  static class CamSwitchListener implements CompoundButton.OnCheckedChangeListener {
    MainActivity mActivity;

    CamSwitchListener(MainActivity activity) {
      mActivity = activity;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
      if (isChecked) {
        mActivity.getCameraInstance();
        mActivity.startCamera();
        FrameLayout layout = mActivity.findViewById(R.id.cam_layout);
        layout.addView(new CamPreview(mActivity, mActivity.getCamera()));
      } else {
        FrameLayout layout = mActivity.findViewById(R.id.cam_layout);
        layout.removeAllViews();
        mActivity.releaseCamera();
      }
    }
  }
}
