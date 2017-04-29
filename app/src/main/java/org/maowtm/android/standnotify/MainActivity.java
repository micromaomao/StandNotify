package org.maowtm.android.standnotify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.ToggleButton;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    protected BroadcastReceiver br;
    protected ToggleButton tog;
    protected Timer ensureStartTimer = null;
    protected ProgressBar progX, progY, progZ, progM;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.tog = (ToggleButton) this.findViewById(R.id.toggleNotify);
        this.progX = (ProgressBar) this.findViewById(R.id.progX);
        this.progY = (ProgressBar) this.findViewById(R.id.progY);
        this.progZ = (ProgressBar) this.findViewById(R.id.progZ);
        this.progM = (ProgressBar) this.findViewById(R.id.progM);
        this.br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                tog.setChecked(true);
                float[] lastAcceleration = intent.getFloatArrayExtra("lastAcceleration");
                float magnitude = (float) (Math.sqrt(Math.pow(lastAcceleration[0], 2) + Math.pow(lastAcceleration[1], 2) + Math.pow(lastAcceleration[2], 2)) * 10);
                if (Build.VERSION.SDK_INT >= 24) {
                    progX.setProgress(Math.round(lastAcceleration[0] * 10), true);
                    progY.setProgress(Math.round(lastAcceleration[1] * 10), true);
                    progZ.setProgress(Math.round(lastAcceleration[2] * 10), true);
                    progM.setProgress(Math.round(magnitude), true);
                } else {
                    progX.setProgress(Math.round(lastAcceleration[0] * 10));
                    progY.setProgress(Math.round(lastAcceleration[1] * 10));
                    progZ.setProgress(Math.round(lastAcceleration[2] * 10));
                    progM.setProgress(Math.round(magnitude));
                }
            }
        };
    }

    protected void onResume() {
        super.onResume();
        this.registerReceiver(this.br, new IntentFilter(MeasuringService.ACTION_PING));
        if (this.ensureStartTimer != null) this.ensureStartTimer.cancel();
        this.ensureStartTimer = new Timer();
        this.ensureStartTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (MainActivity.this.tog.isChecked()) {
                    Intent it = new Intent(MainActivity.this, MeasuringService.class);
                    startService(it);
                }
            }
        }, 500, 500);
    }

    protected void onPause() {
        super.onPause();
        this.unregisterReceiver(this.br);
        this.ensureStartTimer.cancel();
        this.ensureStartTimer = null;
    }

    public void handleToggle(View v) {
        Intent it = new Intent(this, MeasuringService.class);
        if (this.tog.isChecked()) {
            startService(it);
        } else {
            stopService(it);
        }
    }
}
