package br.com.treinohg.app;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.fitness.FitnessLocal;
import com.google.android.gms.fitness.LocalRecordingClient;
import com.google.android.gms.fitness.data.LocalBucket;
import com.google.android.gms.fitness.data.LocalDataPoint;
import com.google.android.gms.fitness.data.LocalDataSet;
import com.google.android.gms.fitness.data.LocalDataType;
import com.google.android.gms.fitness.data.LocalField;
import com.google.android.gms.fitness.request.LocalDataReadRequest;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Passos do celular.
 *
 * 1) Preferencial: Recording API do Google Play services (FitnessLocal). Depois da inscrição,
 *    o próprio Play services conta os passos em segundo plano, com o app fechado, e guarda 10 dias.
 * 2) Reserva: sensor de hardware TYPE_STEP_COUNTER. Ele só conta enquanto alguém mantém o sensor
 *    ativo, então deixamos um listener registrado enquanto o app estiver aberto.
 */
@CapacitorPlugin(
    name = "StepCounter",
    permissions = { @Permission(alias = "activity", strings = { Manifest.permission.ACTIVITY_RECOGNITION }) }
)
public class StepCounterPlugin extends Plugin {

    private static final String PREFS = "growup_steps";
    private SensorEventListener keepAlive;
    private float lastValue = -1f;
    private long lastValueAt = 0L;

    @Override
    public void load() {
        if (granted()) {
            startKeepAlive();
            subscribeRecording(null);
        }
    }

    @Override
    protected void handleOnDestroy() {
        stopKeepAlive();
        super.handleOnDestroy();
    }

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

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private boolean recordingAvailable() {
        try {
            return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getContext(), LocalRecordingClient.LOCAL_RECORDING_CLIENT_MIN_VERSION_CODE) == ConnectionResult.SUCCESS;
        } catch (Throwable t) {
            return false;
        }
    }

    private interface Done { void done(boolean ok); }

    /** Inscrição na Recording API (repetir é seguro: se já existe, não faz nada). */
    private void subscribeRecording(final Done done) {
        if (!granted() || !recordingAvailable()) {
            prefs().edit().putBoolean("recording", false).apply();
            if (done != null) done.done(false);
            return;
        }
        try {
            FitnessLocal.getLocalRecordingClient(getContext())
                .subscribe(LocalDataType.TYPE_STEP_COUNT_DELTA)
                .addOnSuccessListener(v -> {
                    SharedPreferences p = prefs();
                    SharedPreferences.Editor e = p.edit().putBoolean("recording", true);
                    if (!p.contains("since")) e.putLong("since", System.currentTimeMillis());
                    e.apply();
                    if (done != null) done.done(true);
                })
                .addOnFailureListener(e -> {
                    prefs().edit().putBoolean("recording", false).apply();
                    if (done != null) done.done(false);
                });
        } catch (Throwable t) {
            if (done != null) done.done(false);
        }
    }

    /** Mantém o contador de hardware ativo enquanto o app está aberto e guarda o último valor. */
    private void startKeepAlive() {
        if (keepAlive != null) return;
        final Sensor s = sensor();
        final SensorManager sm = manager();
        if (s == null || sm == null || !granted()) return;
        keepAlive = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                lastValue = event.values[0];
                lastValueAt = System.currentTimeMillis();
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };
        try {
            sm.registerListener(keepAlive, s, SensorManager.SENSOR_DELAY_NORMAL, 5_000_000, new Handler(Looper.getMainLooper()));
        } catch (Throwable t) {
            keepAlive = null;
        }
    }

    private void stopKeepAlive() {
        SensorManager sm = manager();
        if (keepAlive != null && sm != null) sm.unregisterListener(keepAlive);
        keepAlive = null;
    }

    @PluginMethod
    public void isAvailable(PluginCall call) {
        JSObject r = new JSObject();
        boolean rec = recordingAvailable();
        r.put("available", sensor() != null || rec);
        r.put("sensor", sensor() != null);
        r.put("recording", rec);
        r.put("granted", granted());
        call.resolve(r);
    }

    @PluginMethod
    public void requestPermission(PluginCall call) {
        if (granted()) {
            afterGrant(call);
            return;
        }
        requestPermissionForAlias("activity", call, "permissionCallback");
    }

    @PermissionCallback
    private void permissionCallback(PluginCall call) {
        if (granted()) {
            afterGrant(call);
            return;
        }
        JSObject r = new JSObject();
        r.put("granted", false);
        call.resolve(r);
    }

    private void afterGrant(final PluginCall call) {
        startKeepAlive();
        subscribeRecording(ok -> {
            JSObject r = new JSObject();
            r.put("granted", true);
            r.put("recording", ok);
            call.resolve(r);
        });
    }

    /** Passos por dia (dias locais) vindos da Recording API, desde a inscrição, até 10 dias. */
    @PluginMethod
    public void daily(final PluginCall call) {
        if (!granted()) {
            call.reject("permission");
            return;
        }
        final int days = Math.max(1, Math.min(10, call.getInt("days", 10)));
        subscribeRecording(ok -> {
            if (!ok) {
                call.reject("norecording");
                return;
            }
            Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            c.add(Calendar.DAY_OF_MONTH, -(days - 1));
            final long from = c.getTimeInMillis();
            final long now = System.currentTimeMillis();
            try {
                LocalDataReadRequest req = new LocalDataReadRequest.Builder()
                    .aggregate(LocalDataType.TYPE_STEP_COUNT_DELTA)
                    .bucketByTime(1, TimeUnit.HOURS)
                    .setTimeRange(from, now, TimeUnit.MILLISECONDS)
                    .build();
                FitnessLocal.getLocalRecordingClient(getContext()).readData(req)
                    .addOnSuccessListener(resp -> {
                        JSArray out = new JSArray();
                        List<LocalBucket> buckets = resp.getBuckets();
                        for (LocalBucket b : buckets) {
                            long steps = 0;
                            for (LocalDataSet set : b.getDataSets()) {
                                for (LocalDataPoint p : set.getDataPoints()) {
                                    steps += p.getValue(LocalField.FIELD_STEPS).asInt();
                                }
                            }
                            if (steps <= 0) continue;
                            JSObject o = new JSObject();
                            o.put("start", (double) b.getStartTime(TimeUnit.MILLISECONDS));
                            o.put("steps", (double) steps);
                            out.put(o);
                        }
                        JSObject r = new JSObject();
                        r.put("buckets", out);
                        r.put("from", (double) from);
                        r.put("since", (double) prefs().getLong("since", now));
                        call.resolve(r);
                    })
                    .addOnFailureListener(e -> call.reject("readfail: " + e.getMessage()));
            } catch (Throwable t) {
                call.reject("readfail: " + t.getMessage());
            }
        });
    }

    /** Valor bruto do contador de hardware (passos desde o boot). */
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
        startKeepAlive();
        if (lastValue >= 0 && System.currentTimeMillis() - lastValueAt < 60_000) {
            call.resolve(result(lastValue));
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
                lastValue = event.values[0];
                lastValueAt = System.currentTimeMillis();
                call.resolve(result(event.values[0]));
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };
        sm.registerListener(listener, s, SensorManager.SENSOR_DELAY_NORMAL, 0, handler);
        handler.postDelayed(() -> {
            if (done[0]) return;
            done[0] = true;
            sm.unregisterListener(listener);
            if (lastValue >= 0) call.resolve(result(lastValue));
            else call.reject("timeout");
        }, 8000);
    }

    private JSObject result(float v) {
        JSObject r = new JSObject();
        r.put("steps", (double) v);
        r.put("bootTime", (double) (System.currentTimeMillis() - SystemClock.elapsedRealtime()));
        return r;
    }
}
