package org.maowtm.android.standnotify;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class MeasuringService extends Service {
    public static final String ACTION_PING = "org.maowtm.android.standnotify.ACTION_PING";
    public static final int A = 30;
    public static final int B = 100;
    protected NotificationManager nm;
    protected SensorManager sm;
    protected Sensor sAcc;
    protected PowerManager pm;
    protected PowerManager.WakeLock wl;
    protected SensorEventListener sel;
    protected Toast lowAccuracyToast = null;
    protected Lock lck;
    protected Timer broadcaster;
    protected float[] lastAcc = {0, 0, 0};
    protected float[] magLastA;
    protected float[] magLastB;
    protected long counter;
    protected long lastAlertedCounter;
    public MeasuringService() {
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    public void onCreate() {
        Toast.makeText(this, R.string.serviceStart, Toast.LENGTH_SHORT).show();

        Notification.Builder nb = new Notification.Builder(this);
        nb.setContentTitle(this.getText(R.string.running));
        nb.setContentText(this.getText(R.string.willnotify));
        nb.setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT));
        if (Build.VERSION.SDK_INT >= 21) {
            nb.setVisibility(Notification.VISIBILITY_PUBLIC);
        }
        nb.setPriority(Notification.PRIORITY_HIGH);
        nb.setWhen(System.currentTimeMillis());
        if (Build.VERSION.SDK_INT >= 17) {
            nb.setShowWhen(true);
        }
        nb.setSmallIcon(R.drawable.ic_notifications_black_24dp);
        Notification n = nb.build();
        startForeground(NotificationConstants.foregroundService, n);

        this.sm = (SensorManager) this.getSystemService(SENSOR_SERVICE);
        this.sAcc = this.sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        this.pm = (PowerManager) this.getSystemService(POWER_SERVICE);
        this.wl = this.pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MeasuringServiceWakeLock");
        this.wl.acquire();
        this.lck = new ReentrantLock();
        this.lck.lock();

        this.magLastA = new float[A];
        this.magLastB = new float[B];
        this.counter = 0;
        this.lastAlertedCounter = B;

        this.broadcaster = new Timer();
        this.broadcaster.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                lck.lock();
                Intent bit = new Intent(ACTION_PING);
                bit.putExtra("lastAcceleration", lastAcc);
                bit.putExtra("lowAccuracy", lowAccuracyToast != null);
                MeasuringService.this.sendBroadcast(bit, ACTION_PING);
                lck.unlock();
            }
        }, 1000, 1000);

        this.sel = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                lck.lock();
                lastAcc = event.values;
                float magnitude = (float) (Math.sqrt(Math.pow(lastAcc[0], 2) + Math.pow(lastAcc[1], 2) + Math.pow(lastAcc[2], 2)));
                magLastA[(int) (counter % A)] = magnitude;
                magLastB[(int) (counter % B)] = magnitude;
                if (counter > B && counter - lastAlertedCounter > B) {
                    float lastAMean = 0;
                    float lastBMean = 0;
                    for (float v : magLastA) {
                        lastAMean += v;
                    }
                    for (float v : magLastB) {
                        lastBMean += v;
                    }
                    lastBMean -= lastAMean;
                    lastAMean /= A;
                    lastBMean /= B;
                    double ratio = lastAMean / Math.max(lastBMean, 0.1);
                    if (ratio > 3.5) {
                        Log.d("reachThreshold", String.format("%f / %f = %f", lastAMean, lastBMean, ratio));
                        MeasuringService.this.alert();
                        lastAlertedCounter = counter;
                    } else if (counter % B == 0) {
                        Log.d("ratio", Double.toString(ratio));
                    }
                }
                if (counter % A == 0) {
                    Log.d("counter", String.format("%d", counter));
                }
                counter++;
                lck.unlock();
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                lck.lock();
                if (accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
                    if (lowAccuracyToast == null) {
                        lowAccuracyToast = Toast.makeText(MeasuringService.this, R.string.lowAccuracy, Toast.LENGTH_LONG);
                        lowAccuracyToast.show();
                    } else {
                        lowAccuracyToast.setDuration(Toast.LENGTH_LONG);
                        lowAccuracyToast.show();
                    }
                } else if (lowAccuracyToast != null) {
                    lowAccuracyToast.cancel();
                    lowAccuracyToast = null;
                }
                lck.unlock();
            }
        };
        sm.registerListener(this.sel, this.sAcc, SensorManager.SENSOR_DELAY_NORMAL);
        this.lck.unlock();
    }

    public void alert() {
        Notification.Builder nb = new Notification.Builder(this);
        nb.setContentTitle(this.getText(R.string.running));
        nb.setContentText(this.getText(R.string.nottext));
        nb.setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT));
        if (Build.VERSION.SDK_INT >= 21) {
            nb.setVisibility(Notification.VISIBILITY_PUBLIC);
        }
        nb.setPriority(Notification.PRIORITY_HIGH);
        nb.setWhen(System.currentTimeMillis());
        if (Build.VERSION.SDK_INT >= 17) {
            nb.setShowWhen(true);
        }
        nb.setSmallIcon(R.drawable.ic_notifications_black_24dp);
        nb.setVibrate(new long[]{0, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 500});
        nb.setSound(null);
        nb.setAutoCancel(true);
        Notification n = nb.build();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(NotificationConstants.alert);
        nm.notify(NotificationConstants.alert, n);
    }

    public void onDestroy() {
        this.stopForeground(true);
        this.lck.lock();
        sm.unregisterListener(this.sel, this.sAcc);
        this.broadcaster.cancel();
        this.wl.release();
        this.lck.unlock();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
