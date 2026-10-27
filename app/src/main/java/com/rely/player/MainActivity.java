package com.rely.player;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.database.Cursor;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_AUDIO   = 2001;
    private static final int REQ_NOTIF   = 2002;

    private WebView web;
    private PlaybackService playbackService;
    private boolean bound = false;

    // ── Service connection ──────────────────────────────────────────────
    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder svc) {
            PlaybackService.LocalBinder b = (PlaybackService.LocalBinder) svc;
            playbackService = b.getService();
            bound = true;
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            bound = false;
        }
    };

    // ── Activity lifecycle ──────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);

        // JS ↔ Android bridge
        web.addJavascriptInterface(new RelyBridge(), "Android");

        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        setBars(night);

        web.setWebViewClient(new WebViewClient());
        web.loadUrl("file:///android_asset/index.html");

        // Bind & start the foreground service
        Intent svc = new Intent(this, PlaybackService.class);
        startService(svc);
        bindService(svc, conn, BIND_AUTO_CREATE);

        requestPermissionsIfNeeded();
    }

    @Override
    protected void onDestroy() {
        if (bound) { unbindService(conn); bound = false; }
        super.onDestroy();
    }

    // ── Runtime permissions ─────────────────────────────────────────────
    private void requestPermissionsIfNeeded() {
        List<String> needed = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO)
                    != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.READ_MEDIA_AUDIO);

            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        if (!needed.isEmpty())
            requestPermissions(needed.toArray(new String[0]), REQ_NOTIF);
    }

    // ── File picker result ──────────────────────────────────────────────
    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_AUDIO && res == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;

            // Persist permission so the service can read it later
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);

            String title = getFileName(uri);
            String js = String.format(
                "window.onFilePicked && window.onFilePicked('%s','%s')",
                uri.toString().replace("'", "\\'"),
                title.replace("'", "\\'"));
            web.evaluateJavascript(js, null);
        }
    }

    private String getFileName(Uri uri) {
        String name = "Unknown";
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = c.getString(idx);
            }
        } catch (Exception ignored) {}
        return name;
    }

    // ── System bar theming ──────────────────────────────────────────────
    @SuppressWarnings("deprecation")
    private void setBars(boolean dark) {
        int c = dark ? 0xFF0B0B0B : 0xFFECECEC;
        Window w = getWindow();
        w.setStatusBarColor(c);
        w.setNavigationBarColor(c);
        View d = w.getDecorView();
        int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                  | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        d.setSystemUiVisibility(dark ? (d.getSystemUiVisibility() & ~light)
                                     : (d.getSystemUiVisibility() | light));
        web.setBackgroundColor(c);
    }

    // ── Back button ─────────────────────────────────────────────────────
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        web.evaluateJavascript("window.relyBack && window.relyBack()", v -> {
            if (!"true".equals(v)) moveTaskToBack(true);
        });
    }

    // ── JS Bridge exposed as window.Android ─────────────────────────────
    private class RelyBridge {

        /** Called from JS to open the system file picker for audio. */
        @JavascriptInterface
        public void pickAudioFile() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("audio/*");
                startActivityForResult(i, REQ_AUDIO);
            });
        }

        /** Play a content:// URI through the foreground service. */
        @JavascriptInterface
        public void play(String uri, String title, String artist) {
            if (bound) playbackService.playUri(uri, title, artist);
        }

        @JavascriptInterface public void pause()  { if (bound) playbackService.pause(); }
        @JavascriptInterface public void resume() { if (bound) playbackService.play();  }

        @JavascriptInterface
        public boolean isPlaying() { return bound && playbackService.isPlaying(); }

        @JavascriptInterface
        public int getPosition() { return bound ? playbackService.getPosition() : 0; }

        @JavascriptInterface
        public int getDuration() { return bound ? playbackService.getDuration() : 0; }

        @JavascriptInterface
        public void seekTo(int ms) { if (bound) playbackService.seekTo(ms); }

        /** Theme change → update system bars. */
        @JavascriptInterface
        public void bar(boolean dark) { runOnUiThread(() -> setBars(dark)); }

        /** Keep screen awake while Now-Playing is open. */
        @JavascriptInterface
        public void keepScreenOn(boolean on) {
            runOnUiThread(() -> {
                Window w = getWindow();
                if (on) w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else    w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }
    }
}
