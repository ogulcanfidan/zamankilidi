package com.zamankilidi.app

import android.graphics.Color
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Uçtan uca (edge-to-edge) ekran desteği.
 *
 * Android 15'ten (API 35) itibaren, targetSdk 35+ olan uygulamalarda sistem
 * içeriği durum ve gezinme çubuklarının ALTINA çiziyor ve temadaki
 * android:statusBarColor / android:navigationBarColor ayarlarını tamamen yok
 * sayıyor. O iki satır bu yüzden themes.xml'den kaldırıldı; çubukların
 * kapladığı alan artık burada dolgu (padding) olarak telafi ediliyor. Aksi
 * halde ekranın başlığı durum çubuğunun, en alttaki banner da gezinme
 * çubuğunun altında kalıyordu.
 */

/**
 * onCreate içinde, setContentView'dan ÖNCE çağrılmalı.
 *
 * Uygulamanın arka planı her koşulda koyu (@color/bg) ve values-night yok;
 * tema DayNight olduğu için sistem açık temadayken varsayılan davranış koyu
 * simge çizmek olurdu ve simgeler koyu zeminde kaybolurdu. SystemBarStyle.dark
 * "zemin koyu, simgeler açık olsun" demek - sistem temasından bağımsız olarak
 * doğru olan bu.
 */
fun ComponentActivity.enableDarkEdgeToEdge() {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
    )
}

/**
 * setContentView'dan SONRA, kök görünüm verilerek çağrılmalı.
 *
 * Düzenin XML'de tanımlı kendi dolgusu kaybolmasın diye başlangıç değerleri
 * bir kez saklanıp her seferinde onun ÜSTÜNE ekleniyor; dinleyici birden çok
 * kez çalışabiliyor (ekran döndürme, klavyenin açılıp kapanması vb.) ve
 * saklamasak dolgu her çağrıda birikirdi.
 *
 * displayCutout da hesaba katılıyor: çentikli/delikli ekranlarda yatay modda
 * metnin kamera deliğinin altında kalmaması için.
 */
fun applySystemBarInsets(root: View) {
    val initialLeft = root.paddingLeft
    val initialTop = root.paddingTop
    val initialRight = root.paddingRight
    val initialBottom = root.paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
        val bars = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        view.updatePadding(
            left = initialLeft + bars.left,
            top = initialTop + bars.top,
            right = initialRight + bars.right,
            bottom = initialBottom + bars.bottom
        )
        windowInsets
    }
}
