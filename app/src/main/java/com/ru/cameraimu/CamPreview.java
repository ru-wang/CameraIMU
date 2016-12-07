package com.ru.cameraimu;

import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class CamPreview extends SurfaceView implements SurfaceHolder.Callback {
  private SurfaceHolder mHolder;
  private Camera mCamera;
  private int mOldW = 0;
  private int mOldH = 0;

  private final String TAG = "TAG/CameraIMU";

  public CamPreview(Context context, Camera camera) {
    super(context);
    mCamera = camera;
    mHolder = getHolder();
    mHolder.addCallback(this);
    mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
  }

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    Log.i(TAG, "surfaceCreated");
    try {
      mCamera.setPreviewDisplay(holder);
      mCamera.startPreview();
    } catch (Exception e) {
      Log.e(TAG, "surfaceCreated: " + e.getMessage());
    }
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
    // Note: Screen orientation should not be changed once starts recording
    Log.i(TAG, "surfaceChanged: " + w + "x" + h);

    if (mHolder.getSurface() == null) {
      Log.e(TAG, "surfaceChanged: " + "mHolder.getSurface() returns null");
      return;
    }

    try {
      mCamera.stopPreview();
    } catch (Exception e) {
      Log.e(TAG, "surfaceChanged: " + e.getMessage());
    }

    if (mOldH != h || mOldW != w) {
      int newH = h;
      int newW = (int) (newH * 1f / MainActivity.DEFAULT_CAPTURE_H * MainActivity.DEFAULT_CAPTURE_W);
      mHolder.setFixedSize(newW, newH);
    }
    mOldW = w;
    mOldH = h;

    try {
      mCamera.setPreviewDisplay(mHolder);
      mCamera.startPreview();
    } catch (Exception e) {
      Log.e(TAG, "surfaceChanged: " + e.getMessage());
    }
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    Log.i(TAG, "surfaceDestroyed");
  }
}
