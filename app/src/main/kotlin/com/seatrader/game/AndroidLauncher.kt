package com.seatrader.game

import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration

class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // при вылете сохраняем причину: игра покажет её при следующем запуске
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = java.io.StringWriter(); e.printStackTrace(java.io.PrintWriter(sw))
                java.io.File(filesDir, "crash.txt").writeText("Версия ${packageManager.getPackageInfo(packageName, 0).versionName}, Android ${android.os.Build.VERSION.RELEASE}, ${android.os.Build.MODEL}\n" + sw.toString().take(4000))
            } catch (_: Throwable) { }
            prev?.uncaughtException(t, e)
        }
        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            useAccelerometer = false
            useCompass = false
            useGyroscope = false
            // сцена рисуется в собственный буфер со сглаживанием FXAA: MSAA экрана не нужен
            // (на старых телефонах он съедает память и может не поддерживаться)
            numSamples = 0
            r = 8; g = 8; b = 8; a = 0
            depth = 16
        }
        // на телефонах с вырезом под камеру — на весь экран (отступы учитываются в интерфейсе)
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.also {
                it.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        initialize(SeaGame(), config)
    }
}
