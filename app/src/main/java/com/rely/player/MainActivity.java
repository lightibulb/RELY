package com.rely.player;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_AUDIO = 2001;
    private static final int REQ_PERMISSIONS = 2002;

    private WebView web;
    private PlaybackService playbackService;
    private boolean bound = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            PlaybackService.LocalBinder binder = (PlaybackService.LocalBinder) service;
            playbackService = binder.getService();
            bound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        setContentView(web);

        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        web.addJavascriptInterface(new RelyBridge(), "Android");

        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;

        setBars(night);

        final boolean localFont = assetExists("fonts/PixelifySans.ttf");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                String inject =
                        "window.RELY_LOCAL_FONT=" + localFont + ";" +
                        "(function(){" +
                        "if(window.__RELY_BRIDGE_INJECTED__)return;" +
                        "window.__RELY_BRIDGE_INJECTED__=true;" +
                        "var s=document.createElement('script');" +
                        "s.src='file:///android_asset/rely_bridge.js';" +
                        "(document.body||document.documentElement).appendChild(s);" +
                        "})();";

                view.evaluateJavascript(inject, null);
            }
        });

        web.loadUrl("file:///android_asset/index.html");

        Intent serviceIntent = new Intent(this, PlaybackService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        bindService(serviceIntent, connection, BIND_AUTO_CREATE);

        requestPermissionsIfNeeded();
    }

    @Override
    protected void onDestroy() {
        if (bound) {
            unbindService(connection);
            bound = false;
        }
        super.onDestroy();
    }

    private boolean assetExists(String path) {
        try {
            getAssets().open(path).close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void requestPermissionsIfNeeded() {
        List<String> needed = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_MEDIA_AUDIO);
            }

            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }

        if (!needed.isEmpty()) {
            requestPermissions(needed.toArray(new String[0]), REQ_PERMISSIONS);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_AUDIO && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) {
                return;
            }

            try {
                getContentResolver().takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
            } catch (SecurityException ignored) {
                // Some providers may not support persistable permissions.
            }

            String title = getFileName(uri);

            String js = String.format(
                    "window.onFilePicked && window.onFilePicked('%s', '%s');",
                    jsEscape(uri.toString()),
                    jsEscape(title)
            );

            web.evaluateJavascript(js, null);
        }
    }

    private String getFileName(Uri uri) {
        String name = "Unknown";

        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    name = cursor.getString(index);
                }
            }
        } catch (Exception ignored) {
        }

        return name;
    }

    private static String jsEscape(String s) {
        return s
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    @SuppressWarnings("deprecation")
    private void setBars(boolean dark) {
        int color = dark ? 0xFF0B0B0B : 0xFFECECEC;

        Window window = getWindow();
        window.setStatusBarColor(color);
        window.setNavigationBarColor(color);

        View decor = window.getDecorView();

        int lightFlags =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR |
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;

        decor.setSystemUiVisibility(
                dark
                        ? (decor.getSystemUiVisibility() & ~lightFlags)
                        : (decor.getSystemUiVisibility() | lightFlags)
        );

        web.setBackgroundColor(color);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        web.evaluateJavascript("window.relyBack && window.relyBack()", value -> {
            if (!"true".equals(value)) {
                moveTaskToBack(true);
            }
        });
    }

    private class RelyBridge {

        @JavascriptInterface
        public void pickAudioFile() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("audio/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(intent, REQ_AUDIO);
            });
        }

        @JavascriptInterface
        public void play(final String uri, final String title, final String artist) {
            runOnUiThread(() -> {
                if (bound && playbackService != null) {
                    playbackService.playUri(uri, title, artist);
                }
            });
        }

        @JavascriptInterface
        public void pause() {
            runOnUiThread(() -> {
                if (bound && playbackService != null) {
                    playbackService.pause();
                }
            });
        }

        @JavascriptInterface
        public void resume() {
            runOnUiThread(() -> {
                if (bound && playbackService != null) {
                    playbackService.play();
                }
            });
        }

        @JavascriptInterface
        public boolean isPlaying() {
            return bound && playbackService != null && playbackService.isPlaying();
        }

        @JavascriptInterface
        public int getPosition() {
            return bound && playbackService != null ? playbackService.getPosition() : 0;
        }

        @JavascriptInterface
        public int getDuration() {
            return bound && playbackService != null ? playbackService.getDuration() : 0;
        }

        @JavascriptInterface
        public void seekTo(int ms) {
            runOnUiThread(() -> {
                if (bound && playbackService != null) {
                    playbackService.seekTo(ms);
                }
            });
        }

        @JavascriptInterface
        public void bar(final boolean dark) {
            runOnUiThread(() -> setBars(dark));
        }

        @JavascriptInterface
        public void keepScreenOn(final boolean on) {
            runOnUiThread(() -> {
                Window window = getWindow();
                if (on) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            });
        }
    }
}
