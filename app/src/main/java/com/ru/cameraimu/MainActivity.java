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
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

  public static final boolean NEED_RECORD = false;
  public static final int DEFAULT_CAPTURE_W = 640;
  public static final int DEFAULT_CAPTURE_H = 480;

  public String mDateString;
  public String mStorageDir;

  // Sensor
  private Sensor mGyroscope;
  private Sensor mAccelerometer;
  private IMUEventListener mGyroListener;  // The gyroscope event listener
  private IMUEventListener mAcceListener;  // The accelerometer event listener
  private SensorManager mSensorManager;

  // Camera
  private Camera mCamera;
  private Boolean mIsCapturing = false;
  private CamCallbacks.ShutterCallback mShutter;
  private CamCallbacks.PictureCallback mPicture;
  private CamPreview mPreview;

  // UI
  private TextView mInfoView;

  // Debug
  private final String TAG = "TAG/CameraIMU";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    Log.i(TAG, "onCreate");
    super.onCreate(savedInstanceState);

    Context context = getApplicationContext();
    setContentView(R.layout.activity_main);

    mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

    if (mAccelerometer == null || mGyroscope == null) {
      Toast toast = Toast.makeText(context, R.string.fail_to_load_imu, Toast.LENGTH_SHORT);
      toast.show();
      finish();
      return;
    }

    if (!isExternalStorageWritable() && NEED_RECORD) {
      Toast toast = Toast.makeText(context, R.string.failed_to_access_external_storage, Toast.LENGTH_SHORT);
      toast.show();
      finish();
      return;
    }

    if (!checkCamera()) {
      Toast toast = Toast.makeText(context, R.string.failed_to_access_camere, Toast.LENGTH_SHORT);
      toast.show();
      finish();
      return;
    }

    getCameraInstance();
    if (mCamera == null) {
      Toast toast = Toast.makeText(context, R.string.failed_to_access_camere, Toast.LENGTH_SHORT);
      toast.show();
      finish();
    }

    if (NEED_RECORD) {
      mDateString = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)).format(Calendar.getInstance().getTime());
      mStorageDir = getResources().getString(R.string.app_name) + File.separator + mDateString;
      File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                           mStorageDir + File.separator + "IMG");
      if (!file.mkdirs()) {
        Toast toast = Toast.makeText(context, R.string.failed_to_access_external_storage, Toast.LENGTH_SHORT);
        toast.show();
        finish();
        return;
      }
    }

    mGyroListener = new IMUEventListener(this, IMUEventListener.TypeE.G);
    mAcceListener = new IMUEventListener(this, IMUEventListener.TypeE.A);

    mPreview = new CamPreview(context, mCamera);
    ((FrameLayout) findViewById(R.id.cam_layout)).addView(mPreview);
    mInfoView = (TextView) findViewById(R.id.info_view);

    mShutter = new CamCallbacks.ShutterCallback();
    mPicture = new CamCallbacks.PictureCallback(this);
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

    getCameraInstance();
    setCamFeatures();
  }

  @Override
  protected void onPause() {
    Log.i(TAG, "onPause");
    super.onPause();
    mSensorManager.unregisterListener(mGyroListener);
    mSensorManager.unregisterListener(mAcceListener);

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

  public Boolean isCapturing() {
    synchronized (mIsCapturing) {
      return mIsCapturing;
    }
  }

  public CamCallbacks.ShutterCallback getShutter() { return mShutter; }
  public CamCallbacks.PictureCallback getPicture() { return mPicture; }

  public void printSensorInfo(long timestampNanos) {
    mInfoView.setText("Sensor Information:\n");

    IMUEventListener.DataTupleT gyroData = mGyroListener.getCurrentTuple();
    IMUEventListener.DataTupleT acceData = mAcceListener.getCurrentTuple();

    mInfoView.append(String.format(Locale.US, "GX: %08.6f\t\tAX: %08.6f\n", gyroData.x(), acceData.x()));
    mInfoView.append(String.format(Locale.US, "GY: %08.6f\t\tAY: %08.6f\n", gyroData.y(), acceData.y()));
    mInfoView.append(String.format(Locale.US, "GZ: %08.6f\t\tAZ: %08.6f\n", gyroData.z(), acceData.z()));
    mInfoView.append(String.format(Locale.US, "Timestamp Nanos: %d", timestampNanos));
  }

  public void onCaptureBtnClick(View view) {
      if (!mIsCapturing) {
        mIsCapturing = true;

        Context context = getApplicationContext();
        Toast toast = Toast.makeText(context, R.string.start_capturing_msg, Toast.LENGTH_SHORT);
        toast.show();
        mCamera.takePicture(mShutter, null, mPicture);
      } else {
        synchronized (mIsCapturing) { mIsCapturing = false; }
        Context context = getApplicationContext();
        Toast toast = Toast.makeText(context, R.string.stop_capturing_msg, Toast.LENGTH_SHORT);
        toast.show();
        if (NEED_RECORD) {
          mGyroListener.flushData();
          mAcceListener.flushData();
        }
      }
  }

  private boolean isExternalStorageWritable() {
    String state = Environment.getExternalStorageState();
    return Environment.MEDIA_MOUNTED.equals(state);
  }

  private boolean checkCamera() {
    if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA))
      return true;
    else
      return false;
  }

  private void getCameraInstance() {
    if (mCamera == null) {
      try {
        mCamera = Camera.open();
      } catch (Exception e) {
        Log.e(TAG, "getCameraInstance: " + e.getMessage());
      }
    }
  }

  private void releaseCamera() {
    if (mCamera != null) {
      mCamera.release();
      mCamera = null;
    }
  }

  private void setCamFeatures() {
    Camera.Parameters params = mCamera.getParameters();

    List<String> focusModes = params.getSupportedFocusModes();
    if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO))
      params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
    List<String> flashModes = params.getSupportedFlashModes();
    if (flashModes.contains(Camera.Parameters.FLASH_MODE_OFF))
      params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
    List<String> whiteBalanceModes = params.getSupportedWhiteBalance();
    if (whiteBalanceModes.contains(Camera.Parameters.WHITE_BALANCE_DAYLIGHT))
      params.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_DAYLIGHT);

    params.setPreviewSize(DEFAULT_CAPTURE_W, DEFAULT_CAPTURE_H);
    params.setPictureSize(DEFAULT_CAPTURE_W, DEFAULT_CAPTURE_H);

    mCamera.setParameters(params);
  }
}
