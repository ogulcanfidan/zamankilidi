package com.zamankilidi.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * Kilidin asıl "koruma" katmanı burası. Ön planda hangi uygulamanın olduğunu
 * her değiştiğinde öğreniyoruz (typeWindowStateChanged). Oturum aktifken:
 *
 *  - Süre dolmuşsa -> her zaman BlockActivity'yi (süre doldu modu) göster.
 *  - Süre dolmamışsa ama açılan uygulama izinli listede değilse -> aynı
 *    BlockActivity'yi (izinsiz uygulama modu) göster.
 *
 * Çocuk kilidi arka plana atıp ana ekrana (launcher) dönmeye çalışsa BİLE,
 * oturum aktifken ana ekranın kendisi de "izinli" sayılmaz - bu servis onu
 * da yakalayıp geri yönlendiriyor, böylece widget/bildirim çubuğu gibi
 * yerlerde dolaşma şansı kalmıyor. Pratikte çok hızlı olduğu için "kaçış"
 * şansı neredeyse hiç kalmıyor.
 *
 * ÖNEMLİ - yanlış alarm (false positive) koruması: Kenardan kaydırma gibi
 * sistem geçiş animasyonları, klavye açılıp kapanması ya da bazı
 * uygulamaların (ör. Instagram Reels) kendi içindeki geçişleri sırasında,
 * Android ANLIK olarak sistemin kendi arayüz bileşenlerini (systemUI,
 * klavye, launcher vb.) ya da uygulamanın kendi ara pencerelerini "yeni bir
 * pencere" olarak bildirebiliyor. Bunlar gerçek bir "uygulama açılışı"
 * değil - bu yüzden hem bilinen sistem paketlerini hariç tutuyoruz hem de
 * her yeni pencere olayında bekleyen bir yönlendirmeyi iptal edip kısa bir
 * süre (250ms) hiç yeni olay gelmezse ancak o zaman kilit ekranını
 * tetikliyoruz. Bu, ekran içeriğini okuma iznine (canRetrieveWindowContent)
 * hiç ihtiyaç duymadan, sadece pencere olaylarının kendisiyle çalışıyor -
 * gizlilik politikamızdaki "ekran içeriğini okumaz" sözü hâlâ geçerli.
 */
class MonitorAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingRedirect: Runnable? = null

    private var lastRedirectedPackage: String? = null
    private var lastRedirectAt = 0L

    companion object {
        // Ayarlar'daki "açık" durumu (isAccessibilityServiceEnabled) ile
        // servisin FİİLEN bağlanıp olay almaya başlaması (onServiceConnected)
        // arasında kısa bir gecikme olabiliyor - özellikle izin YENİ
        // verildiyse. MainActivity, kilidi başlatmadan önce bu bayrakla
        // servisin gerçekten hazır olduğunu doğruluyor (bkz.
        // MainActivity.commitAndStartLock) - yoksa ilk kilit oturumunda
        // birkaç yüz milisaniyelik bu pencerede kaçış mümkün oluyordu.
        @Volatile
        private var connected = false

        fun isRunning(): Boolean = connected

        // Bunlar bir "uygulama" değil, sistemin/klavyenin kendi arayüz
        // bileşenleri. İzinli listede olmasalar bile asla kilit ekranını
        // tetiklememeliler.
        private val SYSTEM_PACKAGES = setOf(
            "com.android.systemui",
            "com.google.android.inputmethod.latin",
            "com.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.touchtype.swiftkey",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.google.android.gms",
            // NOT: gerçek launcher (ana ekran) paketi BİLEREK bu listede
            // DEĞİL - artık oturum aktifken ana ekrana dönmek de normal bir
            // "izinli mi" kontrolünden geçiyor (bkz. onAccessibilityEvent).
            // com.hihonor.systemmanager, MagicOS'ta görev değiştirici/kenar
            // paneli gibi GERÇEK UYGULAMA OLMAYAN geçişlerde araya girebiliyor.
            "com.hihonor.systemmanager",
            // Gerçek cihazda Toast teşhisiyle doğrulandı: Instagram'dan kenardan
            // kaydırarak çıkarken/geri dönerken bu iki paket kısa süreliğine
            // "öndeki pencere" olarak raporlanıyor ve yanlışlıkla kilit ekranını
            // tetikliyordu. "android" - sistemin çekirdek paketi (gesture
            // navigasyon animasyonu, ekran görüntüsü/ses paneli gibi katmanlar
            // hep bu paket adıyla gelir, gerçek bir "uygulama" değil).
            // "com.google.android.googlequicksearchbox" - Google uygulaması /
            // Discover akışı; ana ekrana geçiş sırasında kısaca öne gelen bir
            // sistem bileşeni olarak tetikleniyor.
            "android",
            "com.google.android.googlequicksearchbox"
        )

        // Yönlendirmeden önce bekleyeceğimiz süre. Gerçek bir uygulama
        // açılışında fark edilmiyor; sadece anlık sistem titremelerini
        // eleyip gerçek geçişleri ayırt etmek için var. Kenardan geri gitme /
        // görev değiştirici (recents) animasyonları bazı cihazlarda 250ms'den
        // uzun sürebildiği için biraz daha toleranslı tuttuk.
        private const val SETTLE_DELAY_MS = 400L

        // SYSTEM_PACKAGES tam eşleşme listesi dışında, üreticiye göre değişen
        // (Samsung, Xiaomi vb.) kendi sistem arayüzü / görev değiştirici
        // paketlerini de isim kalıbına bakarak tanımaya çalışıyoruz - sabit
        // paket adı listesi her cihazı kapsayamıyor.
        //
        // NOT: "launcher" ve genel "hihonor" kalıpları BİLEREK burada değil -
        // gerçek ana ekran (launcher) paketinin adı da bu kalıplara uyduğu
        // için, burada tutulsalardı ana ekrana dönmek her zaman "izinli"
        // sayılmaya devam ederdi; oysa artık oturum aktifken ana ekran da
        // normal bir "izinli mi" kontrolünden geçmeli (bkz.
        // onAccessibilityEvent). MagicOS'un systemmanager'ı zaten
        // SYSTEM_PACKAGES'te tam eşleşmeyle ayrıca yakalanıyor.
        private fun looksLikeSystemUiPackage(pkg: String): Boolean {
            val p = pkg.lowercase()
            return p.contains("systemui") ||
                p.contains("recents") ||
                p.contains("overview") ||
                p.contains("taskswitcher")
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = true
    }

    override fun onUnbind(intent: Intent?): Boolean {
        connected = false
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return

        // Her yeni pencere olayı, daha önce planlanmış olası bir
        // yönlendirmeyi iptal eder. Kenardan kaydırma ya da bir uygulama
        // içi geçiş sırasında art arda gelen anlık olaylarda, en son gelen
        // GERÇEK durum kazanır - aradaki titreşim kilit ekranını tetiklemez.
        pendingRedirect?.let { handler.removeCallbacks(it) }
        pendingRedirect = null

        if (pkg == packageName) return
        // DİKKAT: launcherPackage (ana ekran) BİLEREK burada hariç
        // tutulmuyor. Eskiden ana ekrana dönmek her zaman izinliydi; bu da
        // çocuğun kilidi arka plana atıp ana ekranda (widget, bildirim
        // çubuğu, hızlı ayarlar vb.) serbestçe dolaşabilmesine yol açıyordu.
        // Artık launcher da normal bir "izinli mi değil mi" kontrolünden
        // geçiyor - session aktifken ve launcher izinli uygulamalar
        // listesinde değilse (ki normalde değildir), o da yönlendirilir.
        if (pkg in SYSTEM_PACKAGES || looksLikeSystemUiPackage(pkg)) return
        if (!SessionManager.isActive(this)) return

        val timeUp = SessionManager.isTimeUp(this)
        val allowed = !timeUp && SessionManager.isAllowed(this, pkg)
        if (allowed) return

        val check = Runnable { redirectIfNeeded(pkg, timeUp) }
        pendingRedirect = check
        handler.postDelayed(check, SETTLE_DELAY_MS)
    }

    private fun redirectIfNeeded(pkg: String, timeUp: Boolean) {
        // Aynı paket için art arda tetiklenip ekranı titretmesin diye kısa
        // bir aralık koyuyoruz.
        val now = System.currentTimeMillis()
        if (pkg == lastRedirectedPackage && now - lastRedirectAt < 800L) return
        lastRedirectedPackage = pkg
        lastRedirectAt = now

        val mode = if (timeUp) BlockActivity.MODE_TIME_UP else BlockActivity.MODE_BLOCKED_APP
        val intent = Intent(this, BlockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(BlockActivity.EXTRA_MODE, mode)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        // Gerekmiyor.
    }
}
