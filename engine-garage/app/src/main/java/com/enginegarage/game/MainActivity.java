package com.enginegarage.game;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.RenderProcessGoneDetail;
import android.view.ViewGroup;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

/** Вся игра — HTML/JS в assets/index.html, здесь только окно WebView. */
public class MainActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // при вылете сохраняем причину: игра покажет её при следующем запуске
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                StringWriter sw = new StringWriter(); e.printStackTrace(new PrintWriter(sw));
                String head = "Android " + Build.VERSION.RELEASE + ", " + Build.MODEL + "\n";
                FileOutputStream fo = new FileOutputStream(new File(getFilesDir(), "crash.txt"));
                fo.write((head + sw.toString()).substring(0, Math.min(4000, head.length() + sw.toString().length())).getBytes("UTF-8"));
                fo.close();
            } catch (Throwable ignored) { }
            if (prev != null) prev.uncaughtException(t, e);
        });
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        createWeb(savedInstanceState);
    }

    private void createWeb(Bundle savedInstanceState) {
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(21, 23, 27));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setTextZoom(100);
        s.setSupportZoom(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                // движок WebView упал (например, не хватило памяти): пересоздаём окно, а не закрываем игру
                writeCrash("Перезапуск: движок отображения закрылся" + (Build.VERSION.SDK_INT >= 26 && detail.didCrash() ? " с ошибкой" : " (не хватило памяти)"));
                ViewGroup parent = (ViewGroup) view.getParent();
                if (parent != null) parent.removeView(view);
                view.destroy();
                createWeb(null);
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        setContentView(web);
        hideBars();
        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl("file:///android_asset/index.html");
    }

    private void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    private void writeCrash(String text) {
        try {
            FileOutputStream fo = new FileOutputStream(new File(getFilesDir(), "crash.txt"));
            fo.write(text.getBytes("UTF-8")); fo.close();
        } catch (Throwable ignored) { }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.evaluateJavascript("window.onAppPause&&window.onAppPause()", null);
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // игра сама решает: закрыть окно/вернуться на вкладку или выйти
        web.evaluateJavascript("window.onBack?window.onBack():false", value -> {
            if (!"true".equals(value)) finish();
        });
    }

    class Bridge {
        @JavascriptInterface
        public String takeCrash() {
            try {
                File f = new File(getFilesDir(), "crash.txt");
                if (!f.exists()) return null;
                byte[] b = new byte[(int) f.length()];
                java.io.FileInputStream in = new java.io.FileInputStream(f);
                int n = in.read(b); in.close(); f.delete();
                return new String(b, 0, Math.max(0, n), "UTF-8");
            } catch (Throwable e) { return null; }
        }

        @JavascriptInterface
        public void vibrate(int ms) {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null) return;
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(Math.max(1, ms), VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(ms);
        }
    }
}
