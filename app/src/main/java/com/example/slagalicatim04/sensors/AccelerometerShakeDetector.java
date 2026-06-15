package com.example.slagalicatim04.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public final class AccelerometerShakeDetector implements SensorEventListener {
    public interface Listener {
        void onShake();
    }

    private static final float SHAKE_THRESHOLD_GRAVITY = 2.7f;
    private static final long SHAKE_COOLDOWN_MILLIS = 1000L;

    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final Listener listener;
    private boolean running;
    private long lastShakeAt;

    public AccelerometerShakeDetector(Context context, Listener listener) {
        sensorManager = (SensorManager) context.getApplicationContext()
                .getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager == null
                ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        this.listener = listener;
    }

    public boolean start() {
        if (running) {
            return true;
        }
        if (sensorManager == null || accelerometer == null) {
            return false;
        }
        running = sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_GAME
        );
        return running;
    }

    public void stop() {
        if (sensorManager != null && running) {
            sensorManager.unregisterListener(this);
        }
        running = false;
    }

    public boolean isAvailable() {
        return accelerometer != null;
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!running || event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
            return;
        }

        float gravityX = event.values[0] / SensorManager.GRAVITY_EARTH;
        float gravityY = event.values[1] / SensorManager.GRAVITY_EARTH;
        float gravityZ = event.values[2] / SensorManager.GRAVITY_EARTH;
        float gravityForce = (float) Math.sqrt(
                gravityX * gravityX + gravityY * gravityY + gravityZ * gravityZ
        );

        long now = System.currentTimeMillis();
        if (gravityForce >= SHAKE_THRESHOLD_GRAVITY
                && now - lastShakeAt >= SHAKE_COOLDOWN_MILLIS) {
            lastShakeAt = now;
            listener.onShake();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
