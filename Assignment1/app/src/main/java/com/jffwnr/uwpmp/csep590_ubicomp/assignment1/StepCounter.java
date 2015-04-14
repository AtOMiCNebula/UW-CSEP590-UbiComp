package com.jffwnr.uwpmp.csep590_ubicomp.assignment1;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.util.Arrays;

import static android.content.Context.SENSOR_SERVICE;


public class StepCounter implements SensorEventListener {
    private static int FILTER_NEIGHBORS = 5; // median filter looks for 5 elements in either direction
    private static float THRESHOLD_ACCELERATION = 0.75f; // acceleration we must surpass to be eligible

    private MainActivity _mainActivity;
    private SensorManager _sensorManager;
    private Sensor _accelerometer;
    private CircularBuffer _bufferRaw;
    private CircularBuffer _bufferMedian;
    private int _lastReportedCount;

    public StepCounter(MainActivity mainActivity) {
        _mainActivity = mainActivity;

        _sensorManager = (SensorManager)_mainActivity.getSystemService(SENSOR_SERVICE);
        _accelerometer = _sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        _bufferRaw = new CircularBuffer(200); // TODO size is appropriate?
        _bufferMedian = new CircularBuffer(200); // TODO size is appropriate?
        _lastReportedCount = 0;
    }

    public void onResume() {
        _sensorManager.registerListener(this, _accelerometer, SensorManager.SENSOR_DELAY_GAME);
    }

    public void onPause() {
        _sensorManager.unregisterListener(this);
    }

    public int getStepCount() {
        return _lastReportedCount;
    }

    public void setStepCount(int count) {
        _lastReportedCount = count;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        double magnitude = (float)Math.sqrt(
                Math.pow(event.values[0], 2) +
                Math.pow(event.values[1], 2) +
                Math.pow(event.values[2], 2)
        );
        _bufferRaw.add(event.timestamp, magnitude);

        // Update median filtered data
        if (_bufferRaw.size() >= (FILTER_NEIGHBORS*2+1)) {
            double values[] = new double[FILTER_NEIGHBORS*2+1];
            for (int i = 0; i < FILTER_NEIGHBORS*2+1; i++) {
                values[i] = _bufferRaw.getValue(i);
            }
            Arrays.sort(values);
            _bufferMedian.add(event.timestamp, values[FILTER_NEIGHBORS]);
        }

        // De-mean data (using all median data, which will change over time as the buffer circles around)
        double mean = 0.0f;
        for (int i = 0; i < _bufferMedian.size(); i++) {
            mean += _bufferMedian.getValue(i);
        }
        mean /= _bufferMedian.size();

        // Look for a zero crossing
        if (_bufferMedian.size() > 1) {
            double checkCurrent = _bufferMedian.getValue(0) - (mean + THRESHOLD_ACCELERATION);
            double checkPrevious = _bufferMedian.getValue(1) - (mean + THRESHOLD_ACCELERATION);
            if (checkPrevious < 0 && checkCurrent > 0) {
                // We got one!
                _lastReportedCount++;
                _mainActivity.onStepCountUpdate(_lastReportedCount);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
