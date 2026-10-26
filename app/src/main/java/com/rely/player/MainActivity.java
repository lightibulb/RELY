package com.rely.player;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);                    // likes + theme are kept in localStorage
        s.setMediaPlaybackRequiresUserGesture(false);

        // The page tells us when the theme changes so the system bars can follow it.
        web.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void bar(final boolean dark) {
                runOnUiThread(() -> setBars(dark));
            }
        }, "Android");

        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        setBars(night);

        web.setWebViewClient(new WebViewClient());
        web.loadUrl("file:///android_asset/index.html");
    }

    @SuppressWarnings("deprecation")
    private void setBars(boolean dark) {
        int c = dark ? 0xFF0B0B0B : 0xFFECECEC;
        Window w = getWindow();
        w.setStatusBarColor(c);
        w.setNavigationBarColor(c);
        View d = w.getDecorView();
        int f = d.getSystemUiVisibility();
        int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        d.setSystemUiVisibility(dark ? (f & ~light) : (f | light));
        web.setBackgroundColor(c);
    }

    // Back closes the Now Playing sheet first, then sends the app to the background
    // (music keeps playing) instead of killing it.
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        web.evaluateJavascript("window.relyBack&&window.relyBack()", v -> {
            if (!"true".equals(v)) moveTaskToBack(true);
        });
    }
}
