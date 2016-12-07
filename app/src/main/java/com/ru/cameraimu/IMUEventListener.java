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

public class IMUEventListener implements SensorEventListener {

  public enum TypeE {
    G,  // GYROSCOPE
    A,  // ACCELEROMETER
  }

  public class DataTupleT {
    private float vx;
    private float vy;
    private float vz;
    private long timestampNanos;

    DataTupleT(float[] v, long timestamp) {
      vx = v[0];
      vy = v[1];
      vz = v[2];
      this.timestampNanos = timestamp;
    }

    DataTupleT(float vx, float vy, float vz, long timestamp) {
      this.vx = vx;
      this.vy = vy;
      this.vz = vz;
      this.timestampNanos = timestamp;
    }

    @Override
    public String toString() {
      return String.format(Locale.US, "%08.6f %08.6f %08.6f %d\n", vx, vy, vz, timestampNanos);
    }

    public float x() { return vx; }
    public float y() { return vy; }
    public float z() { return vz; }
  }

  private class OutputAsyncTask extends AsyncTask<LinkedList<?>, Void, Void> {
    @Override @SuppressWarnings("unchecked")
    protected Void doInBackground(LinkedList<?>... params) {
      LinkedList<DataTupleT> sequenceToBeSerialized = (LinkedList<DataTupleT>) (params[0]);

      String filename = String.format(Locale.US, mType + "%08d.txt", mSerializedSequencesNum++);
      File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                           mActivity.mStorageDir + File.separator + filename);

      // Write file
      try {
        FileOutputStream fos = new FileOutputStream(file);
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fos));
        for (DataTupleT tuple : sequenceToBeSerialized)
          writer.write(mType + " " + tuple.toString());
        writer.flush();
      } catch (IOException e) {
        Log.e(TAG, "doInBackground: " + e.getMessage());
      }

      return null;
    }
  }

  // Upper size limit of a single data sequence
  // A write operation is executed when this is reached
  private final int SINGLE_SEQUENCE_LIMIT = 500;

  private int mSerializedSequencesNum = 0;

  private MainActivity mActivity;
  private TypeE mType;
  private DataTupleT mCurrentTuple;
  private LinkedList<DataTupleT> mSequence;

  private final String TAG = "TAG/CameraIMU";

  public IMUEventListener(MainActivity activity, TypeE type) {
    mActivity = activity;
    mType = type;
    mCurrentTuple = new DataTupleT(0, 0, 0, 0);
    mSequence = null;
  }

  @Override
  public void onSensorChanged(SensorEvent event) {
    if (mActivity.NEED_RECORD && mActivity.isCapturing())
      recordData(event.values, event.timestamp);

    mCurrentTuple.vx = event.values[0];
    mCurrentTuple.vy = event.values[1];
    mCurrentTuple.vz = event.values[2];
    mCurrentTuple.timestampNanos = event.timestamp;

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
    if (mType == TypeE.A)
      s = (String) (mActivity.getResources().getText(R.string.acce_accuracy_changed_to));
    else
      s = (String) (mActivity.getResources().getText(R.string.gyro_accuracy_changed_to));
    Toast.makeText(mActivity.getApplicationContext(), s + accuracyChars, Toast.LENGTH_SHORT).show();
  }

  public DataTupleT getCurrentTuple() { return mCurrentTuple; }

  public void flushData() {
    // Instantiate an AsyncTask to flush the data onto the disk
    // in order to prevent blocking
    LinkedList<DataTupleT> asyncSequence = mSequence;
    mSequence = null;
    new OutputAsyncTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, asyncSequence);
  }

  private void recordData(float[] v, long timestampNanos) {
    if (mSequence == null)
      mSequence = new LinkedList<DataTupleT>();
    mSequence.add(new DataTupleT(v, timestampNanos));

    // When a single data sequence reached its upper limit
    // instantiate an AsyncTask to execute the writing
    // in order to prevent blocking
    if (mSequence.size() == SINGLE_SEQUENCE_LIMIT) {
      LinkedList<DataTupleT> asyncSequence = mSequence;
      mSequence = new LinkedList<DataTupleT>();
      new OutputAsyncTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, asyncSequence);
    }
  }
}
