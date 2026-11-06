package br.com.treinohg.app;

import android.Manifest;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

/**
 * Lê o contador de passos de hardware do celular (TYPE_STEP_COUNTER).
 * O sensor conta passos desde o último boot, mesmo com o app fechado;
 * o app web calcula os passos de cada dia a partir das leituras.
 */
@CapacitorPlugin(
    name = "StepCounter",
    permissions = { @Permission(alias = "activity", strings = { Manifest.permission.ACTIVITY_RECOGNITION }) }
)
public class StepCounterPlugin extends Plugin {

    private SensorManager manager() {
        return (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
    }

    private Sensor sensor() {
        SensorManager sm = manager();
        return sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
    }

    private boolean granted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true;
        return getPermissionState("activity") == PermissionState.GRANTED;
    }

    @PluginMethod
    public void isAvailable(PluginCall call) {
        JSObject r = new JSObject();
        r.put("available", sensor() != null);
        r.put("granted", granted());
        call.resolve(r);
    }

    @PluginMethod
    public void requestPermission(PluginCall call) {
        if (granted()) {
            JSObject r = new JSObject();
            r.put("granted", true);
            call.resolve(r);
            return;
        }
        requestPermissionForAlias("activity", call, "permissionCallback");
    }

    @PermissionCallback
    private void permissionCallback(PluginCall call) {
        JSObject r = new JSObject();
        r.put("granted", granted());
        call.resolve(r);
    }

    @PluginMethod
    public void read(final PluginCall call) {
        final Sensor s = sensor();
        if (s == null) {
            call.reject("unavailable");
            return;
        }
        if (!granted()) {
            call.reject("permission");
            return;
        }
        final SensorManager sm = manager();
        final boolean[] done = { false };
        final Handler handler = new Handler(Looper.getMainLooper());
        final SensorEventListener listener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if (done[0]) return;
                done[0] = true;
                sm.unregisterListener(this);
                JSObject r = new JSObject();
                r.put("steps", (double) event.values[0]);
                r.put("bootTime", (double) (System.currentTimeMillis() - SystemClock.elapsedRealtime()));
                call.resolve(r);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };
        sm.registerListener(listener, s, SensorManager.SENSOR_DELAY_NORMAL, 0, handler);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (done[0]) return;
                done[0] = true;
                sm.unregisterListener(listener);
                call.reject("timeout");
            }
        }, 8000);
    }
}
