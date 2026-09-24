package com.zamankilidi.app

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Tüm kalıcı durumu (PIN, izinli uygulamalar, oturum bitiş zamanı) tek yerden
 * yönetir. SharedPreferences kullanıyoruz çünkü veri çok küçük (birkaç alan) -
 * ayrı bir veritabanına gerek yok.
 */
object SessionManager {

    private const val PREFS = "zaman_kilidi_prefs"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_SALT = "pin_salt"
    private const val KEY_RECOVERY_HASH = "recovery_hash"
    private const val KEY_RECOVERY_SALT = "recovery_salt"
    private const val KEY_RECOVERY_QUESTION_INDEX = "recovery_question_index"
    private const val KEY_ALLOWED_APPS = "allowed_apps"
    private const val KEY_SESSION_END_AT = "session_end_at"
    private const val KEY_SESSION_ACTIVE = "session_active"
    private const val KEY_LAST_INTERSTITIAL_AT = "last_interstitial_at"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- PIN ----------

    fun hasPin(context: Context): Boolean =
        prefs(context).contains(KEY_PIN_HASH)

    fun setPin(context: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hashPin(pin, salt)
        prefs(context).edit()
            .putString(KEY_PIN_SALT, salt.joinToString(",") { it.toString() })
            .putString(KEY_PIN_HASH, hash)
            .apply()
    }

    fun checkPin(context: Context, pin: String): Boolean {
        val p = prefs(context)
        val saltStr = p.getString(KEY_PIN_SALT, null) ?: return false
        val storedHash = p.getString(KEY_PIN_HASH, null) ?: return false
        val salt = saltStr.split(",").map { it.toByte() }.toByteArray()
        return hashPin(pin, salt) == storedHash
    }

    private fun hashPin(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        val bytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ---------- Kurtarma cevabı ----------
    // PIN unutulduğunda, oturum aktifken Ayarlar'a gidip uygulama verisini
    // temizlemek pratikte MÜMKÜN DEĞİL - MonitorAccessibilityService, izinli
    // olmayan Ayarlar uygulamasını da anında kilit ekranına geri
    // yönlendiriyor. Bu yüzden PIN ilk belirlenirken, ebeveynin bilip
    // çocuğun kolayca tahmin edemeyeceği bir "kurtarma cevabı" da
    // belirlenmesi ZORUNLU - kilit ekranında "PIN'imi unuttum" ile bu cevap
    // doğrulanarak, Ayarlar'a hiç gitmeden anında kilit kaldırılabiliyor.

    fun hasRecoveryAnswer(context: Context): Boolean =
        prefs(context).contains(KEY_RECOVERY_HASH)

    // questionIndex, önceden tanımlı soru listesindeki (recovery_questions)
    // seçilen sorunun konumu. Cevap gibi gizli değil, düz metin olarak
    // saklanıyor - kurtarma ekranında hangi soruyu göstereceğimizi bilmemiz
    // için gerekli, güvenlik cevaba (hash'lenmiş) dayanıyor.
    fun setRecoveryAnswer(context: Context, questionIndex: Int, answer: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hashPin(normalizeRecoveryAnswer(answer), salt)
        prefs(context).edit()
            .putString(KEY_RECOVERY_SALT, salt.joinToString(",") { it.toString() })
            .putString(KEY_RECOVERY_HASH, hash)
            .putInt(KEY_RECOVERY_QUESTION_INDEX, questionIndex)
            .apply()
    }

    fun getRecoveryQuestionIndex(context: Context): Int? {
        val p = prefs(context)
        if (!p.contains(KEY_RECOVERY_QUESTION_INDEX)) return null
        return p.getInt(KEY_RECOVERY_QUESTION_INDEX, -1).takeIf { it >= 0 }
    }

    // Hem seçilen sorunun hem de cevabın doğru olması gerekiyor - yanlış
    // soru için doğru bir cevap girilmiş olsa bile kilit açılmamalı.
    fun checkRecoveryAnswer(context: Context, questionIndex: Int, answer: String): Boolean {
        val p = prefs(context)
        val storedIndex = getRecoveryQuestionIndex(context) ?: return false
        if (storedIndex != questionIndex) return false
        val saltStr = p.getString(KEY_RECOVERY_SALT, null) ?: return false
        val storedHash = p.getString(KEY_RECOVERY_HASH, null) ?: return false
        val salt = saltStr.split(",").map { it.toByte() }.toByteArray()
        return hashPin(normalizeRecoveryAnswer(answer), salt) == storedHash
    }

    // Büyük/küçük harf ve baştaki/sondaki boşluk farkı yüzünden doğru
    // cevabın "yanlış" sayılmasını önlemek için normalize ediyoruz.
    private fun normalizeRecoveryAnswer(answer: String): String =
        answer.trim().lowercase()

    // ---------- İzinli uygulamalar ----------

    fun getAllowedApps(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_ALLOWED_APPS, emptySet()) ?: emptySet()

    fun setAllowedApps(context: Context, packages: Set<String>) {
        prefs(context).edit().putStringSet(KEY_ALLOWED_APPS, packages).apply()
    }

    fun isAllowed(context: Context, packageName: String): Boolean {
        // Kendi uygulamamıza ve sistem arayüzüne her zaman izin ver, yoksa
        // kendi ekranlarımızı bile açamayız.
        if (packageName == context.packageName) return true
        if (packageName == "com.android.systemui") return true
        return getAllowedApps(context).contains(packageName)
    }

    // ---------- Oturum ----------

    fun startSession(context: Context, durationMinutes: Int) {
        val endAt = System.currentTimeMillis() + durationMinutes * 60_000L
        prefs(context).edit()
            .putLong(KEY_SESSION_END_AT, endAt)
            .putBoolean(KEY_SESSION_ACTIVE, true)
            .apply()
    }

    fun endSession(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_SESSION_ACTIVE, false)
            .remove(KEY_SESSION_END_AT)
            .apply()
    }

    fun isActive(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SESSION_ACTIVE, false)

    fun sessionEndAt(context: Context): Long =
        prefs(context).getLong(KEY_SESSION_END_AT, 0L)

    fun isTimeUp(context: Context): Boolean =
        isActive(context) && System.currentTimeMillis() >= sessionEndAt(context)

    fun remainingMillis(context: Context): Long =
        (sessionEndAt(context) - System.currentTimeMillis()).coerceAtLeast(0L)

    // ---------- Geçiş reklamı sıklığı ----------
    // Ebeveyn telefonu sık sık kontrol ediyorsa (kilidi aç, bak, yeniden
    // kur) her açılışta tam ekran reklam görmesin. Oturumlar arasında da
    // geçerli olması gerektiği için zaman damgası SharedPreferences'ta
    // tutuluyor; endSession bunu SİLMEZ.

    private const val INTERSTITIAL_COOLDOWN_MS = 3 * 60_000L

    fun canShowInterstitial(context: Context): Boolean {
        val last = prefs(context).getLong(KEY_LAST_INTERSTITIAL_AT, 0L)
        val now = System.currentTimeMillis()
        // Kullanıcı saati geri alırsa (last > now) sınır kalıcı olarak
        // kilitlenmesin diye bu durumu da "gösterilebilir" sayıyoruz.
        if (last > now) return true
        return now - last >= INTERSTITIAL_COOLDOWN_MS
    }

    fun markInterstitialShown(context: Context) {
        prefs(context).edit()
            .putLong(KEY_LAST_INTERSTITIAL_AT, System.currentTimeMillis())
            .apply()
    }
}
