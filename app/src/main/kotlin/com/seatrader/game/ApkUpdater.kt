package com.seatrader.game

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Обновление из игры: качает APK в кэш и ставит его поверх себя через PackageInstaller.
 * Android покажет одно системное окно «Установить?» (и при первом разе — разрешение на установку из игры).
 */
class ApkUpdater(private val act: Activity) : Updater {
    @Volatile override var stage = UPD_IDLE; private set
    @Volatile override var done = 0L; private set
    @Volatile override var total = 0L; private set
    @Volatile override var error = ""; private set
    @Volatile private var cancelled = false
    private var worker: Thread? = null
    private var receiverOn = false

    private val action = act.packageName + ".UPDATE_STATUS"

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (val st = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                        else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    if (confirm != null) {
                        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try { act.startActivity(confirm) } catch (_: Exception) { fail("не открылось окно установки") }
                    }
                }
                PackageInstaller.STATUS_SUCCESS -> stage = UPD_INSTALL
                PackageInstaller.STATUS_FAILURE_ABORTED -> fail("установка отменена")
                else -> fail(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "ошибка установки ($st)")
            }
        }
    }

    private fun fail(msg: String) { error = msg; stage = UPD_ERROR }

    private var lastUrl = ""

    /** Вернулись из настроек Android: если разрешение дали — сразу продолжаем загрузку. */
    fun onResume() {
        if (stage == UPD_PERMISSION && lastUrl.isNotEmpty() && (Build.VERSION.SDK_INT < 26 || act.packageManager.canRequestPackageInstalls())) start(lastUrl)
    }

    override fun start(url: String) {
        if (stage == UPD_LOADING) return
        lastUrl = url
        // Android 8+: игре нужно разрешение «Установка из этого источника»
        if (Build.VERSION.SDK_INT >= 26 && !act.packageManager.canRequestPackageInstalls()) {
            stage = UPD_PERMISSION
            act.runOnUiThread {
                try { act.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + act.packageName))) } catch (_: Exception) { }
            }
            return
        }
        registerOnce()
        cancelled = false; error = ""; done = 0L; total = 0L
        stage = UPD_LOADING
        worker = Thread({ run(url) }, "apk-update").also { it.isDaemon = true; it.start() }
    }

    override fun cancel() {
        cancelled = true
        if (stage == UPD_LOADING) stage = UPD_IDLE
    }

    private fun run(url: String) {
        val file = File(act.cacheDir, "update.apk")
        try {
            download(url, file)
            if (cancelled) return
            if (file.length() < 1_000_000L) { fail("файл повреждён"); return }
            val info = act.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            if (info?.packageName != act.packageName) { fail("файл повреждён"); return }
            install(file)
        } catch (e: Exception) {
            if (!cancelled) fail(if (e is java.io.IOException) "нет связи с сервером" else (e.message ?: "ошибка"))
        }
    }

    private fun download(url: String, file: File) {
        var u = URL(url)
        var conn: HttpURLConnection
        // GitHub перенаправляет на хранилище файлов — идём по переадресациям сами (надёжнее на старых Android)
        var hops = 0
        while (true) {
            conn = u.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15000; conn.readTimeout = 20000
            conn.setRequestProperty("User-Agent", "SeaTrader-Updater")
            val code = conn.responseCode
            if (code in 300..399 && hops++ < 6) {
                val loc = conn.getHeaderField("Location") ?: throw java.io.IOException("redirect")
                conn.disconnect(); u = URL(u, loc); continue
            }
            if (code != 200) throw java.io.IOException("HTTP $code")
            break
        }
        total = if (Build.VERSION.SDK_INT >= 24) conn.contentLengthLong else conn.contentLength.toLong()
        conn.inputStream.use { input ->
            file.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (!cancelled) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                }
            }
        }
        conn.disconnect()
    }

    private fun install(file: File) {
        val pi = act.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setSize(file.length())
        val id = pi.createSession(params)
        pi.openSession(id).use { session ->
            file.inputStream().use { input ->
                session.openWrite("base.apk", 0, file.length()).use { out ->
                    input.copyTo(out, 64 * 1024)
                    session.fsync(out)
                }
            }
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= 31) flags = flags or PendingIntent.FLAG_MUTABLE
            val intent = Intent(action).setPackage(act.packageName)
            val pending = PendingIntent.getBroadcast(act, id, intent, flags)
            stage = UPD_INSTALL
            session.commit(pending.intentSender)
        }
    }

    @Synchronized private fun registerOnce() {
        if (receiverOn) return
        receiverOn = true
        val filter = IntentFilter(action)
        if (Build.VERSION.SDK_INT >= 33) act.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else act.registerReceiver(receiver, filter)
    }
}
