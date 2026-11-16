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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import org.json.JSONObject;

/** Вся игра — HTML/JS в assets/index.html, здесь только окно WebView. */
public class MainActivity extends Activity {
    private WebView web;
    private static final String UPD_BASE = "https://github.com/fillin669-sketch/-/releases/download/motor-garage-latest/";
    private boolean pendingInstall = false;
    private volatile boolean downloading = false;

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
        // вернулись из настроек «Установка неизвестных приложений» — продолжаем установку
        if (pendingInstall && (Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls())) {
            pendingInstall = false;
            installApk();
        }
    }

    private void js(final String code) {
        runOnUiThread(() -> { if (web != null) web.evaluateJavascript(code, null); });
    }

    private HttpURLConnection open(String url) throws Exception {
        // GitHub отдаёт файлы релиза через перенаправление на другой адрес
        for (int i = 0; i < 6; i++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000); c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "MotorGarage-Android");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) { String loc = c.getHeaderField("Location"); c.disconnect(); url = new URL(new URL(url), loc).toString(); continue; }
            if (code != 200) { c.disconnect(); throw new Exception("HTTP " + code); }
            return c;
        }
        throw new Exception("too many redirects");
    }

    private int versionCode() {
        try {
            android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? (int) pi.getLongVersionCode() : pi.versionCode;
        } catch (Throwable e) { return 0; }
    }

    private void installApk() {
        File f = ApkProvider.apkFile(this);
        if (!f.exists()) { js("window.onUpdateProgress&&onUpdateProgress(-1)"); return; }
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            pendingInstall = true;
            js("window.onUpdatePermission&&onUpdatePermission()");
            try { startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))); }
            catch (Throwable e) { pendingInstall = false; }
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            Uri uri;
            if (Build.VERSION.SDK_INT >= 24) {
                uri = Uri.parse("content://" + ApkProvider.AUTH + "/MotorGarage.apk");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                // старые Android: установщик читает файл напрямую
                File ext = new File(getExternalCacheDir() != null ? getExternalCacheDir() : getCacheDir(), "MotorGarage.apk");
                copy(f, ext);
                ext.setReadable(true, false);
                uri = Uri.fromFile(ext);
            }
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable e) {
            js("window.onUpdateProgress&&onUpdateProgress(-1)");
        }
    }

    private static void copy(File a, File b) throws Exception {
        InputStream in = new java.io.FileInputStream(a); OutputStream out = new FileOutputStream(b);
        byte[] buf = new byte[65536]; int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close(); out.close();
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
        public int appVersion() { return versionCode(); }

        @JavascriptInterface
        public void checkUpdate() {
            new Thread(() -> {
                try {
                    HttpURLConnection c = open(UPD_BASE + "version.json?t=" + System.currentTimeMillis());
                    InputStream in = c.getInputStream();
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[8192]; int n;
                    while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                    in.close(); c.disconnect();
                    JSONObject o = new JSONObject(bo.toString("UTF-8"));
                    js("window.onUpdateInfo&&onUpdateInfo(" + o.toString() + ")");
                } catch (Throwable e) {
                    js("window.onUpdateInfo&&onUpdateInfo(null)");
                }
            }).start();
        }

        @JavascriptInterface
        public void downloadUpdate() {
            if (downloading) return;
            downloading = true;
            new Thread(() -> {
                File f = ApkProvider.apkFile(MainActivity.this);
                File tmp = new File(f.getParentFile(), "part.tmp");
                try {
                    HttpURLConnection c = open(UPD_BASE + "MotorGarage.apk");
                    long total = c.getContentLength();
                    InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(tmp);
                    byte[] buf = new byte[65536]; int n; long got = 0, lastT = 0;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n); got += n;
                        long now = System.currentTimeMillis();
                        if (total > 0 && now - lastT > 150) { lastT = now; js("window.onUpdateProgress&&onUpdateProgress(" + Math.min(0.99, (double) got / total) + ")"); }
                    }
                    in.close(); out.close(); c.disconnect();
                    if (got < 10000) throw new Exception("file too small");
                    if (f.exists()) f.delete();
                    if (!tmp.renameTo(f)) throw new Exception("rename");
                    js("window.onUpdateProgress&&onUpdateProgress(1)");
                    runOnUiThread(MainActivity.this::installApk);
                } catch (Throwable e) {
                    tmp.delete();
                    js("window.onUpdateProgress&&onUpdateProgress(-1)");
                } finally { downloading = false; }
            }).start();
        }

        @JavascriptInterface
        public void installUpdate() { runOnUiThread(MainActivity.this::installApk); }

        @JavascriptInterface
        public void vibrate(int ms) {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null) return;
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(Math.max(1, ms), VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(ms);
        }
    }
}
