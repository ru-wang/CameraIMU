package com.ru.cameraimu;

import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

class CamPreview extends SurfaceView implements SurfaceHolder.Callback {
  private static final String TAG = "TAG/CameraIMU";

  private Camera mCamera;

  CamPreview(Context context, Camera camera) {
    super(context);
    mCamera = camera;
    getHolder().addCallback(this);
  }

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    Log.i(TAG, "surfaceCreated");
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
    // Note: Screen orientation should not be changed once starts recording
    Log.i(TAG, "surfaceChanged: " + w + "x" + h);

    if (holder.getSurface() == null) {
      Log.e(TAG, "surfaceChanged: " + "holder.getSurface() returns null");
      return;
    }

    mCamera.stopPreview();

    try {
      mCamera.setPreviewDisplay(holder);
    } catch (Exception e) {
      Log.e(TAG, "surfaceChanged: " + e.getMessage());
    }

    mCamera.startPreview();
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    Log.i(TAG, "surfaceDestroyed");
  }
}
