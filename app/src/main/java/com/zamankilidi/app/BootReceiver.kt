package com.zamankilidi.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Telefon yeniden başlatıldığında aktif oturumu BİLEREK sonlandırır.
 *
 * Bu bir emniyet supabı: PIN'ini ve kurtarma sorusunun cevabını birlikte
 * unutan bir ebeveynin telefonu kalıcı olarak kilitli kalmasın diye,
 * yeniden başlatma her zaman kilidi kaldırır.
 *
 * Alıcı olmadan bu davranış üreticiye kalıyordu: MagicOS/MIUI gibi bazı
 * arayüzler erişilebilirlik servisini boot'ta kapattığı için kilit orada
 * kendiliğinden düşüyor, stock Android'de ise servis geri geldiği için
 * kilit devam ediyordu. SessionManager sadece SharedPreferences'a baktığı
 * ve bitiş saati duvar saati olduğu için, oturum yeniden başlatmayı tek
 * başına atlatabiliyor. Burada oturumu açıkça kapatarak davranışı her
 * cihazda aynı hale getiriyoruz.
 *
 * Karşılığında çocuk, telefonu kapatıp açarak kilitten çıkabilir. Bu
 * bilinçli bir takas: ebeveynin telefonunu tamamen kaybetme riski, bu
 * kaçış yolundan daha ağır basıyor.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        if (!SessionManager.isActive(context)) return

        SessionManager.endSession(context)
        // Oturum zaten bittiği için servis normalde ayakta olmamalı; yine de
        // (ör. üretici "uygulamayı yeniden başlat" davranışıyla) canlandıysa
        // geri sayım bildirimi ekranda asılı kalmasın.
        TimerService.stop(context)
    }
}
