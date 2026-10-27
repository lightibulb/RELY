package com.rely.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import java.io.IOException;

/**
 * Foreground service that owns the MediaPlayer.
 * Keeps music alive even when the WebView is in the background.
 */
public class PlaybackService extends Service
        implements MediaPlayer.OnPreparedListener,
                   MediaPlayer.OnCompletionListener,
                   MediaPlayer.OnErrorListener {

    public static final String CHANNEL_ID   = "rely_playback";
    public static final int    NOTIF_ID     = 1001;

    private MediaPlayer player;
    private final IBinder binder = new LocalBinder();
    private boolean isPrepared = false;

    // ── Binder ──────────────────────────────────────────────────────────
    public class LocalBinder extends Binder {
        PlaybackService getService() { return PlaybackService.this; }
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    // ── Lifecycle ───────────────────────────────────────────────────────
    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        player = new MediaPlayer();
        player.setWakeMode(getApplicationContext(),
                PowerManager.PARTIAL_WAKE_LOCK);
        player.setOnPreparedListener(this);
        player.setOnCompletionListener(this);
        player.setOnErrorListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Must call startForeground within 5 s on API 26+
        startForeground(NOTIF_ID, buildNotification("RELY", "Ready"));
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (player != null) {
            if (player.isPlaying()) player.stop();
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    // ── Public API called from MainActivity / JS ────────────────────────
    public void playUri(String uriString, String title, String artist) {
        try {
            player.reset();
            isPrepared = false;
            Uri uri = Uri.parse(uriString);
            player.setDataSource(getApplicationContext(), uri);
            player.prepareAsync();          // async → onPrepared fires
            updateNotification(title, artist);
        } catch (IOException | IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    public void play()  { if (isPrepared && !player.isPlaying()) player.start(); }
    public void pause() { if (isPrepared &&  player.isPlaying()) player.pause(); }
    public boolean isPlaying() { return isPrepared && player.isPlaying(); }

    public void seekTo(int ms) { if (isPrepared) player.seekTo(ms); }
    public int  getPosition()  { return isPrepared ? player.getCurrentPosition() : 0; }
    public int  getDuration()  { return isPrepared ? player.getDuration() : 0; }

    // ── MediaPlayer callbacks ───────────────────────────────────────────
    @Override public void onPrepared(MediaPlayer mp) {
        isPrepared = true;
        mp.start();
    }

    @Override public void onCompletion(MediaPlayer mp) {
        // JS side can poll this or we can post a JS callback
    }

    @Override public boolean onError(MediaPlayer mp, int what, int extra) {
        isPrepared = false;
        return true;
    }

    // ── Notification helpers ────────────────────────────────────────────
    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Playback",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("RELY music playback controls");
            getSystemService(NotificationManager.class)
                    .createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String title, String subtitle) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        return b
                .setContentTitle(title)
                .setContentText(subtitle)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String title, String subtitle) {
        getSystemService(NotificationManager.class)
                .notify(NOTIF_ID, buildNotification(title, subtitle));
    }
}
