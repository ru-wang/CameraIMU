package com.ru.cameraimu;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.LinkedList;
import java.util.Locale;

class IMUEventListener implements SensorEventListener {

  enum SensorType { GYRO, ACCE }

  static class SensorReading {
    private float mx, my, mz;
    private long mTimestampNanos;

    SensorReading(float[] v, long timestamp) {
      mx = v[0]; my = v[1]; mz = v[2];
      this.mTimestampNanos = timestamp;
    }

    SensorReading(float vx, float vy, float vz, long timestamp) {
      this.mx = vx; this.my = vy; this.mz = vz;
      this.mTimestampNanos = timestamp;
    }

    @Override
    public String toString() {
      return String.format(Locale.US, "%d %f %f %f\n", mTimestampNanos, mx, my, mz);
    }

    float x() { return mx; }
    float y() { return my; }
    float z() { return mz; }
  }

  static class WriteSensorAsyncTask extends AsyncTask<LinkedList<?>, Void, Void> {
    String mPrefix;
    SensorType mType;
    int mSerializedSequencesNum;

    WriteSensorAsyncTask(String prefix, SensorType type, int serializedSequencesNum) {
      mPrefix = prefix;
      mType = type;
      mSerializedSequencesNum = serializedSequencesNum;
    }

    protected Void doInBackground(LinkedList<?>... params) {
      LinkedList<?> sequenceToBeSerialized = params[0];

      String type = mType.toString().toLowerCase();
      String filename = String.format(Locale.US, type + "_%010d.txt", mSerializedSequencesNum);
      File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                           mPrefix + File.separator + filename);

      // Write file
      try {
        FileOutputStream fos = new FileOutputStream(file);
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fos));
        for (Object reading : sequenceToBeSerialized)
          writer.write(reading.toString());
        writer.flush();
        writer.close();
      } catch (IOException e) {
        Log.e(TAG, "doInBackground: " + e.getMessage());
      }

      return null;
    }
  }

  // Upper size limit of a single data sequence
  // A write operation is executed when this is reached
  private static final int SINGLE_SEQUENCE_LIMIT = 10000;

  private int mSerializedSequencesNum = 0;

  private MainActivity mActivity;
  private SensorType mType;
  private SensorReading mCurrentReading;
  private LinkedList<SensorReading> mSequence;

  private static final String TAG = "TAG/CameraIMU";

  IMUEventListener(MainActivity activity, SensorType type) {
    mActivity = activity;
    mType = type;
    mCurrentReading = new SensorReading(0, 0, 0, 0);
    mSequence = null;
  }

  @Override
  public void onSensorChanged(SensorEvent event) {
    if (mActivity.isCapturing())
      recordData(event.values, event.timestamp);

    mCurrentReading.mx = event.values[0];
    mCurrentReading.my = event.values[1];
    mCurrentReading.mz = event.values[2];
    mCurrentReading.mTimestampNanos = event.timestamp;

    mActivity.printSensorInfo(event.timestamp);
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {
    String accuracyChars;
    switch (accuracy) {
      case SensorManager.SENSOR_STATUS_ACCURACY_HIGH:
        accuracyChars = "HIGH";
        break;
      case SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM:
        accuracyChars = "MEDIUM";
        break;
      case SensorManager.SENSOR_STATUS_ACCURACY_LOW:
        accuracyChars = "LOW";
        break;
      case SensorManager.SENSOR_STATUS_UNRELIABLE:
        accuracyChars = "UNRELIABLE";
        break;
      default:
        accuracyChars = "UNKNOWN";
        break;
    }

    String s;
    if (mType == SensorType.ACCE)
      s = (String) (mActivity.getResources().getText(R.string.acce_accuracy_changed_to));
    else
      s = (String) (mActivity.getResources().getText(R.string.gyro_accuracy_changed_to));
    Toast.makeText(mActivity.getApplicationContext(), s + accuracyChars, Toast.LENGTH_SHORT).show();
  }

  void reset() {
    mSerializedSequencesNum = 0;
  }

  SensorReading getCurrentReading() {
    return mCurrentReading;
  }

  void flushData() {
    // Instantiate an AsyncTask to flush the data onto the disk
    // in order to prevent blocking
    LinkedList<SensorReading> readings = mSequence;
    mSequence = null;
    Log.i(TAG, "Flushing " + readings.size() + mType.toString() + "s");
    new WriteSensorAsyncTask(mActivity.getStorageDir(), mType, mSerializedSequencesNum++)
        .execute(readings);
  }

  private void recordData(float[] v, long timestampNanos) {
    if (mSequence == null)
      mSequence = new LinkedList<>();
    mSequence.add(new SensorReading(v, timestampNanos));

    // When a single data sequence reached its upper limit
    // instantiate an AsyncTask to execute the writing
    // in order to prevent blocking
    if (mSequence.size() == SINGLE_SEQUENCE_LIMIT) {
      LinkedList<SensorReading> readings = mSequence;
      mSequence = new LinkedList<>();
      new WriteSensorAsyncTask(mActivity.getStorageDir(), mType, mSerializedSequencesNum++)
          .execute(readings);
    }
  }
}
