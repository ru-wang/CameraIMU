package com.ru.cameraimu;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

  static final int INFO_VIEW_UPDATE_RATE = 5;

  private static final String TAG = "TAG/CameraIMU";

  // Sensor
  private Sensor mGyroscope;
  private Sensor mAccelerometer;
  private IMUEventListener mGyroListener;  // The gyroscope event listener
  private IMUEventListener mAcceListener;  // The accelerometer event listener
  private SensorManager mSensorManager;

  // Camera
  private boolean mIsCapturing = false;
  private Camera mCamera;
  private Camera.Size mPreviewSize = null;
  private Camera.Size mCaptureSize = null;
  private CamCallbacks.PreviewCallback mPreviewCallback;

  // UI
  private String mStorageDir;
  private TextView mInfoView;
  private Switch mCamSwitch;
  private CamCallbacks.CamSwitchListener mCamSwitchListener;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    Log.i(TAG, "onCreate");
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_main);
    mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED);

    if (mAccelerometer == null || mGyroscope == null) {
      Toast toast = Toast.makeText(this, R.string.fail_to_load_imu, Toast.LENGTH_LONG);
      toast.show();
      finish();
      return;
    }

    if (!isExternalStorageWritable()) {
      Toast toast = Toast.makeText(this, R.string.failed_to_access_external_storage, Toast.LENGTH_LONG);
      toast.show();
      finish();
      return;
    }

    if (!checkCamera()) {
      Toast toast = Toast.makeText(this, R.string.failed_to_access_camere, Toast.LENGTH_LONG);
      toast.show();
      finish();
      return;
    }

    getCameraInstance();
    if (mCamera == null) {
      Toast toast = Toast.makeText(this, R.string.failed_to_access_camere, Toast.LENGTH_LONG);
      toast.show();
      finish();
    }

    mGyroListener = new IMUEventListener(this, IMUEventListener.SensorType.GYRO);
    mAcceListener = new IMUEventListener(this, IMUEventListener.SensorType.ACCE);
    mCamSwitchListener = new CamCallbacks.CamSwitchListener(this);
    mCamSwitch = findViewById(R.id.cam_sw);
    mCamSwitch.setOnCheckedChangeListener(mCamSwitchListener);

    if (mCamSwitch.isChecked())
      ((FrameLayout) findViewById(R.id.cam_layout)).addView(new CamPreview(this, mCamera));

    mInfoView = findViewById(R.id.info_view);

    mPreviewCallback = new CamCallbacks.PreviewCallback(this);
  }

  @Override
  public void onStart() {
    Log.i(TAG, "onStart");
    super.onStart();
  }

  @Override
  protected void onResume() {
    Log.i(TAG, "onResume");
    super.onResume();
    mSensorManager.registerListener(mGyroListener, mGyroscope, SensorManager.SENSOR_DELAY_FASTEST);
    mSensorManager.registerListener(mAcceListener, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);

    if (mCamera != null)
      startCamera();
  }

  @Override
  protected void onPause() {
    Log.i(TAG, "onPause");
    super.onPause();
    mSensorManager.unregisterListener(mGyroListener);
    mSensorManager.unregisterListener(mAcceListener);

    if (mCamera != null)
      releaseCamera();
  }

  @Override
  protected void onStop() {
    Log.i(TAG, "onStop");
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    Log.i(TAG, "onDestroy");
    super.onDestroy();
  }

  public void onCaptureBtnClick(View view) {
    if (!mIsCapturing) {
      Switch cam_switch = findViewById(R.id.cam_sw);
      cam_switch.setEnabled(false);
      Toast toast = Toast.makeText(this, R.string.start_capturing_msg, Toast.LENGTH_SHORT);
      toast.show();

      String date = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)).format(Calendar.getInstance().getTime());
      mStorageDir = getResources().getString(R.string.app_name) + File.separator + date;
      File file;
      if (mCamera != null) {
        file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                        mStorageDir + File.separator + "IMG");
        mCamera.setPreviewCallback(mPreviewCallback);
      } else {
        file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                        mStorageDir);
      }
      if (!file.mkdirs()) {
        toast = Toast.makeText(this, R.string.failed_to_access_external_storage, Toast.LENGTH_SHORT);
        toast.show();
        return;
      }

      mIsCapturing = true;
    } else {
      Switch cam_switch = findViewById(R.id.cam_sw);
      cam_switch.setEnabled(true);
      Toast toast = Toast.makeText(this, R.string.stop_capturing_msg, Toast.LENGTH_SHORT);
      toast.show();

      mGyroListener.flushData();
      mAcceListener.flushData();
      if (mCamera != null)
        mCamera.setPreviewCallback(null);

      mIsCapturing = false;
      mGyroListener.reset();
      mAcceListener.reset();
      mPreviewCallback.reset();
    }
  }

  String getStorageDir() {
    return mStorageDir;
  }

  Camera getCamera() {
    return mCamera;
  }

  boolean isCapturing() {
    return mIsCapturing;
  }

  void getCameraInstance() {
    try {
      mCamera = Camera.open();
    } catch (Exception e) {
      Log.e(TAG, "getCameraInstance: " + e.getMessage());
    }
  }

  void startCamera() {
    setCamFeatures();

    Camera.Size previewSize = mCamera.getParameters().getPreviewSize();
    Camera.Size captureSize = mCamera.getParameters().getPictureSize();
    Toast toast = Toast.makeText(
        this,
        "preview " + previewSize.width + " x " + previewSize.height + "\n" +
        "capture " + captureSize.width + " x " + captureSize.height,
        Toast.LENGTH_LONG);
    toast.setGravity(Gravity.CENTER, 0, 0);
    toast.show();
  }

  void releaseCamera() {
    mCamera.stopPreview();
    mCamera.release();
    mCamera = null;
  }

  void printSensorInfo(long timestampNanos) {
    mInfoView.setText("sensor info:\n");

    IMUEventListener.SensorReading gyroData = mGyroListener.getCurrentReading();
    IMUEventListener.SensorReading acceData = mAcceListener.getCurrentReading();

    mInfoView.append(String.format(Locale.US, "gyro: %6.2f\t%6.2f\t%6.2f\n", acceData.x(), acceData.y(), acceData.z()));
    mInfoView.append(String.format(Locale.US, "acce: %6.2f\t%6.2f\t%6.2f\n", gyroData.x(), gyroData.y(), gyroData.z()));
    mInfoView.append(String.format(Locale.US, "nanos: %d", timestampNanos));
    if (mIsCapturing && mCamSwitch.isChecked()) {
      if (mPreviewSize != null)
        mInfoView.append(String.format(Locale.US, "\npreview: %dx%d", mPreviewSize.width, mPreviewSize.height));
      if (mCaptureSize != null)
        mInfoView.append(String.format(Locale.US, "\ncapture: %dx%d", mCaptureSize.width, mCaptureSize.height));
      mInfoView.append(String.format(Locale.US, "\nfps: %.2f", mPreviewCallback.getCurrentFPS()));
    }
  }

  private boolean isExternalStorageWritable() {
    String state = Environment.getExternalStorageState();
    return Environment.MEDIA_MOUNTED.equals(state);
  }

  private boolean checkCamera() {
    return getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
  }

  private void setCamFeatures() {
    Camera.Parameters params = mCamera.getParameters();

    List<String> focusModes = params.getSupportedFocusModes();
    if (focusModes.contains(Camera.Parameters.FOCUS_MODE_INFINITY))
      params.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
    List<String> flashModes = params.getSupportedFlashModes();
    if (flashModes != null && flashModes.contains(Camera.Parameters.FLASH_MODE_OFF))
      params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
    List<String> whiteBalanceModes = params.getSupportedWhiteBalance();
    if (whiteBalanceModes.contains(Camera.Parameters.WHITE_BALANCE_DAYLIGHT))
      params.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_DAYLIGHT);

    List<Camera.Size> preview_sizes = params.getSupportedPreviewSizes();
    List<Camera.Size> capture_sizes = params.getSupportedPictureSizes();
    mPreviewSize = preview_sizes.get(0);
    mCaptureSize = capture_sizes.get(0);

    for (Camera.Size size : preview_sizes) {
      if (size.height >= mPreviewSize.height &&
          size.width >= mPreviewSize.width) {
        mPreviewSize.width = size.width;
        mPreviewSize.height = size.height;
      }
    }

    for (Camera.Size size : capture_sizes) {
      if (size.width * mPreviewSize.height == size.height * mPreviewSize.width) {
        if (mCaptureSize.height <= mPreviewSize.height &&
            mCaptureSize.height <= size.height)
          mCaptureSize = size;
        else if (mPreviewSize.height <= size.height &&
                 mCaptureSize.height >= size.height)
          mCaptureSize = size;
      }
    }

    params.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
    params.setPictureSize(mCaptureSize.width, mCaptureSize.height);

    mCamera.setParameters(params);
  }
}
