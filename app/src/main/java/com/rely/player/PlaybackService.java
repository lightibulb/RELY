package com.rely.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import java.io.IOException;

public class PlaybackService extends Service
        implements MediaPlayer.OnPreparedListener,
                   MediaPlayer.OnCompletionListener,
                   MediaPlayer.OnErrorListener {

    public static final String CHANNEL_ID = "rely_playback";
    public static final int NOTIF_ID = 1001;

    private MediaPlayer player;
    private final IBinder binder = new LocalBinder();
    private boolean isPrepared = false;

    public class LocalBinder extends Binder {
        PlaybackService getService() {
            return PlaybackService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();

        player = new MediaPlayer();
        player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        player.setOnPreparedListener(this);
        player.setOnCompletionListener(this);
        player.setOnErrorListener(this);

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        player.setAudioAttributes(attrs);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundCompat();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (player != null) {
            if (player.isPlaying()) {
                player.stop();
            }
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    public synchronized void playUri(String uriString, String title, String artist) {
        try {
            player.reset();
            isPrepared = false;

            Uri uri = Uri.parse(uriString);
            player.setDataSource(getApplicationContext(), uri);
            player.prepareAsync();

            updateNotification(title, artist);
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            e.printStackTrace();
        }
    }

    public synchronized void play() {
        if (isPrepared && !player.isPlaying()) {
            player.start();
        }
    }

    public synchronized void pause() {
        if (isPrepared && player.isPlaying()) {
            player.pause();
        }
    }

    public synchronized boolean isPlaying() {
        return isPrepared && player.isPlaying();
    }

    public synchronized void seekTo(int ms) {
        if (isPrepared) {
            player.seekTo(ms);
        }
    }

    public synchronized int getPosition() {
        return isPrepared ? player.getCurrentPosition() : 0;
    }

    public synchronized int getDuration() {
        return isPrepared ? player.getDuration() : 0;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        isPrepared = true;
        mp.start();
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        // The JS layer can poll isPlaying()/getPosition() if it needs completion behavior.
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        isPrepared = false;
        return true;
    }

    private void startForegroundCompat() {
        Notification notification = buildNotification("RELY", "Ready");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            );
        } else {
            startForeground(NOTIF_ID, notification);
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Playback",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("RELY music playback controls");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String title, String subtitle) {
        Intent intent = new Intent(this, MainActivity.class);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
                .setContentTitle(title)
                .setContentText(subtitle)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String title, String subtitle) {
        getSystemService(NotificationManager.class)
                .notify(NOTIF_ID, buildNotification(title, subtitle));
    }
}
