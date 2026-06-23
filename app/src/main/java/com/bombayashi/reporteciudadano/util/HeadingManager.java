package com.bombayashi.reporteciudadano.util;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

public class HeadingManager implements SensorEventListener {

    private static final String TAG = "HeadingManager";
    private static final float ALPHA = 0.15f; // Smoothing factor for low-pass filter
    private static final long UPDATE_THRESHOLD_MS = 100; // Update at most every 100ms

    private final SensorManager sensorManager;
    private final Sensor magnetometer;
    private final Sensor accelerometer;
    private final Callback callback;

    private float[] magneticField = new float[3];
    private float[] accelerometerReading = new float[3];
    private float[] rotationMatrix = new float[9];
    private float[] orientationAngles = new float[3];

    private float currentHeading = 0f;
    private float smoothedHeading = 0f;
    private long lastUpdateTime = 0;
    private boolean isListening = false;

    public interface Callback {
        void onHeadingChanged(float heading);
    }

    public HeadingManager(Context context, Callback callback) {
        this.callback = callback;
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.magnetometer = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) : null;
        this.accelerometer = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) : null;

        if (magnetometer == null || accelerometer == null) {
            Log.w(TAG, "⚠️ Magnetometer or Accelerometer not available on this device");
        }
    }

    public void startListening() {
        if (isListening || sensorManager == null) return;

        if (magnetometer != null) {
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
        }
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }

        isListening = true;
        Log.d(TAG, "✓ Heading manager started");
    }

    public void stopListening() {
        if (!isListening || sensorManager == null) return;

        sensorManager.unregisterListener(this);
        isListening = false;
        Log.d(TAG, "✓ Heading manager stopped");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            magneticField = lowPassFilter(event.values, magneticField);
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accelerometerReading = lowPassFilter(event.values, accelerometerReading);
        }

        updateOrientation();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No action needed
    }

    private float[] lowPassFilter(float[] input, float[] output) {
        if (output == null) return input.clone();
        for (int i = 0; i < input.length; i++) {
            output[i] = output[i] + ALPHA * (input[i] - output[i]);
        }
        return output;
    }

    private void updateOrientation() {
        if (SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magneticField)) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles);

            // Convert from radians to degrees
            // azimuth is orientationAngles[0] (0-360 degrees, 0=North, 90=East, 180=South, 270=West)
            currentHeading = (float) Math.toDegrees(orientationAngles[0]);

            // Ensure heading is 0-360
            if (currentHeading < 0) {
                currentHeading += 360f;
            }

            long now = System.currentTimeMillis();
            if (now - lastUpdateTime >= UPDATE_THRESHOLD_MS) {
                smoothedHeading = smoothHeading(smoothedHeading, currentHeading);
                callback.onHeadingChanged(smoothedHeading);
                lastUpdateTime = now;
            }
        }
    }

    private float smoothHeading(float from, float to) {
        // Shortest path on circle (0-360)
        float diff = to - from;
        if (diff > 180f) diff -= 360f;
        if (diff < -180f) diff += 360f;

        float smoothed = from + diff * ALPHA;
        if (smoothed < 0) smoothed += 360f;
        if (smoothed >= 360f) smoothed -= 360f;
        return smoothed;
    }

    public float getCurrentHeading() {
        return smoothedHeading;
    }

    public boolean isAvailable() {
        return magnetometer != null && accelerometer != null;
    }
}
