package com.liveimager.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/** Foreground service required by Android to hold the system-audio capture permission. */
public class CaptureService extends Service {
    static final String EXTRA_CODE = "code", EXTRA_DATA = "data";
    private static final String CHANNEL = "capture";

    private MediaProjection projection;
    private CaptureSource source;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "System audio analysis",
                NotificationManager.IMPORTANCE_LOW));
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Live Imager")
                .setContentText("Analyzing system audio")
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(1, n);
        }
        if (intent == null || Build.VERSION.SDK_INT < 29) {
            stopSelf();
            return START_NOT_STICKY;
        }

        int code = intent.getIntExtra(EXTRA_CODE, 0);
        Intent data = Build.VERSION.SDK_INT >= 33
                ? intent.getParcelableExtra(EXTRA_DATA, Intent.class)
                : intent.getParcelableExtra(EXTRA_DATA);
        try {
            releaseProjection();
            MediaProjectionManager mpm = getSystemService(MediaProjectionManager.class);
            projection = mpm.getMediaProjection(code, data);
            if (projection == null) throw new IllegalStateException("permission not granted");
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() {
                    CaptureSource s = source;
                    if (s != null) Engine.get().stopIf(s);
                    stopSelf();
                }
            }, new Handler(Looper.getMainLooper()));
            source = new CaptureSource(projection);
            Engine.get().start(source, "◉ System audio — analysis only");
        } catch (Exception e) {
            Engine.get().setStatus("System capture failed: " + e.getMessage());
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void releaseProjection() {
        MediaProjection p = projection;
        projection = null;
        if (p != null) p.stop();
    }

    @Override
    public void onDestroy() {
        CaptureSource s = source;
        source = null;
        if (s != null) Engine.get().stopIf(s);
        releaseProjection();
        super.onDestroy();
    }
}
