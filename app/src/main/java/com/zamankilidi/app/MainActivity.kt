package com.zamankilidi.app

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.zamankilidi.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private enum class SortMode { RECENT, MOST_USED, ALPHABETICAL }

    private enum class FlowStep { NOTIFICATION, ACCESSIBILITY_DISCLOSURE, ACCESSIBILITY_SYSTEM_SETTINGS, DEVICE_ADMIN }

    companion object {
        // Bu izin-akışı durumu BİLEREK companion object'te (yani MainActivity
        // ÖRNEĞİNE değil, uygulama sürecine bağlı) tutuluyor. Bazı cihazlarda
        // (ör. bu Honor/MagicOS cihazında) izin ekranları arasında geçiş
        // yaparken MainActivity'nin kendisi beklenmedik şekilde yok edilip
        // yeniden oluşturulabiliyor - eğer bu değişkenler örnek bazlı olsaydı,
        // her yeni MainActivity örneği "pending=null" ile sıfırdan başlar ve
        // akışın hangi adımda olduğunu unutup aynı izin ekranını bir daha
        // açardı. Static tutmak, kaç kere yeniden oluşturulursa oluşturulsun
        // tek bir gerçek durumun olmasını garantiliyor - aynı adımın iki kere
        // başlatılması artık yapısal olarak imkansız.

        // Kullanıcı "İzinleri ayarla"ya bastıktan sonra true olur; her adımdan
        // (bildirim izni / erişilebilirlik / cihaz yöneticisi) döndüğümüzde
        // tekrar butona basmasına gerek kalmadan bir sonraki eksik izne
        // otomatik geçmemizi sağlar - böylece kullanıcı için tek bir akış
        // gibi hissettirir.
        private var permissionFlowActive = false

        // Akışın şu an hangi adımda "sonuç bekliyor" olduğunu tutuyoruz.
        // continuePermissionFlowIfActive() bu doluyken yeni bir ekran
        // başlatmaz (reentrancy koruması) - her adımın kendi launcher
        // callback'i bunu temizleyip akışı bir sonraki adıma ilerletir.
        private var pendingFlowStep: FlowStep? = null
    }

    private lateinit var binding: ActivityMainBinding
    private var appList: MutableList<AppInfo> = mutableListOf()
    private var usageMap: Map<String, UsageStatsHelper.UsageInfo> = emptyMap()
    private var appAdapter: AppListAdapter? = null
    // Varsayılan olarak alfabetik seçili: bu, Kullanım Erişimi izni
    // gerektirmeyen tek sıralama modu. "Son kullanılan"/"En çok kullanılan"
    // isteyen kullanıcıdan izin, o modu seçtiği anda (onSortModeChanged)
    // ayrıca isteniyor - böylece bu izin herkese baştan dayatılmıyor.
    private var currentSortMode = SortMode.ALPHABETICAL
    private var pendingSortMode: SortMode? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Kullanıcı izin verse de vermese de akışa devam ediyoruz -
            // bildirim izni olmadan da kilit çalışır, sadece bildirim görünmez.
            pendingFlowStep = null
            continuePermissionFlowIfActive()
        }

    // Erişilebilirlik iznini istemeden önce açıklama ekranını gösterir.
    // Kullanıcı "Anladım, devam et"e basarsa (RESULT_OK) sistemin
    // Erişilebilirlik ayarları ekranını açıyoruz; "Şimdi değil"e basarsa
    // ya da geri tuşuna basarsa akışı burada durduruyoruz.
    private val accessibilityDisclosureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                pendingFlowStep = FlowStep.ACCESSIBILITY_SYSTEM_SETTINGS
                Toast.makeText(this, getString(R.string.toast_find_open_app), Toast.LENGTH_LONG).show()
                accessibilitySystemSettingsLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else {
                pendingFlowStep = null
                permissionFlowActive = false
                refreshPermissionsCard()
            }
        }

    // AŞAĞIDAKİ ÜÇ LAUNCHER - sistemin kendi Ayarlar ekranlarına
    // (Erişilebilirlik / Cihaz Yöneticisi / Kullanım Erişimi) gidip
    // dönüşü artık onResume()'un "sanırım kullanıcı geri döndü" tahminine
    // değil, Android'in kendi garantili sonuç-bildirimi mekanizmasına
    // (registerForActivityResult) bağlıyoruz. Video kaydıyla doğrulandı:
    // bu Honor/MagicOS cihazında, dış bir Ayarlar ekranına geçerken
    // onResume() beklenmedik şekilde bir kez daha (erken/sahte) tetiklenip
    // "ayarlardan dönüldü" sanıp akışı erken ilerletiyor ve aynı izin
    // ekranının bir daha açılmasına sebep oluyordu. launch() ile kayıtlı
    // bir sonuç callback'i, gerçekten o ekrandan dönülene kadar bir daha
    // tetiklenmeyeceği için bu sorunu kökten ortadan kaldırıyor.
    private val accessibilitySystemSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            pendingFlowStep = null
            continuePermissionFlowIfActive()
        }

    private val deviceAdminLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            pendingFlowStep = null
            continuePermissionFlowIfActive()
        }

    // Bu launcher SADECE sıralama modu değiştirilirken (onSortModeChanged)
    // kullanılıyor - Kullanım Erişimi ana izin akışının bir parçası değil.
    private val usageAccessLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val mode = pendingSortMode
            pendingSortMode = null
            if (mode != null && UsageStatsHelper.hasUsageAccess(this)) {
                setSortModeSilently(mode)
                appAdapter?.updateList(sortedAppList())
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableDarkEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)

        binding.btnGrantPermissions.setOnClickListener { openPermissionFlow() }
        binding.btnStart.setOnClickListener { onStartClicked() }
        binding.rgSort.setOnCheckedChangeListener { _, checkedId -> onSortModeChanged(checkedId) }
        setupDurationSelection()
        setupRecoveryQuestionSpinner()
        maybeShowPermissionIntroPopup()

        // Banner reklam - bu ekran (ana ayar ekranı) tamamen ebeveyne ait,
        // çocuk buraya PIN'siz gelemiyor zaten (bkz. onResume).
        MobileAds.initialize(this) {}
        binding.adViewMain.loadAd(AdRequest.Builder().build())
    }

    override fun onPause() {
        super.onPause()
        binding.adViewMain.pause()
    }

    override fun onDestroy() {
        binding.adViewMain.destroy()
        super.onDestroy()
    }

    /**
     * "Diğer" (özel süre) radio butonu, XML'de 15/30/60 dakikalık butonların
     * bulunduğu rgDuration'ın DIŞINDA ayrı bir satırda duruyor (yanındaki
     * EditText ile birlikte görünebilmesi için). Bu yüzden Android'in
     * RadioGroup'u onu otomatik olarak grubun bir parçası saymıyor - 15/30/60
     * kendi aralarında birbirini otomatik olarak kapatıyor ama "Diğer" hiçbiri
     * tarafından kapatılmıyor, "Diğer" de onları kapatmıyor. Bu yüzden dördünün
     * arasındaki tek seçim kuralını burada elle yönetiyoruz.
     */
    private fun setupDurationSelection() {
        // Bir süre çipi (15/30/60) seçilince "Diğer" işaretini kaldır.
        binding.rgDuration.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId != -1) {
                binding.rbCustom.isChecked = false
            }
        }
        // "Diğer" butonuna basınca çiplerin işaretini kaldır.
        binding.rbCustom.setOnClickListener {
            binding.rgDuration.clearCheck()
            binding.rbCustom.isChecked = true
        }
        // Kullanıcı doğrudan alana dokunup yazmaya başlarsa da otomatik
        // olarak "Diğer" moduna geçelim - radyo düğmesine ayrıca basmasına
        // gerek kalmasın.
        binding.etCustomMinutes.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.rgDuration.clearCheck()
                binding.rbCustom.isChecked = true
            }
        }
    }

    // spRecoveryQuestion'ı önceden tanımlı soru listesiyle (recovery_questions)
    // doldurur ve daha önce bir kurtarma cevabı kaydedilmişse o zaman seçilen
    // soruyu önceden işaretler - kullanıcı hangi soruyu seçtiğini unutmuşsa
    // bile burada görüp hatırlayabilir.
    private fun setupRecoveryQuestionSpinner() {
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.recovery_questions,
            android.R.layout.simple_spinner_item
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.spRecoveryQuestion.adapter = adapter

        SessionManager.getRecoveryQuestionIndex(this)?.let { savedIndex ->
            if (savedIndex in 0 until adapter.count) {
                binding.spRecoveryQuestion.setSelection(savedIndex)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.adViewMain.resume()

        // Aktif bir oturum varsa (ör. bildirime tıklandıysa ya da kullanıcı
        // ana ekrana dönüp uygulamayı tekrar açtıysa) burada ayar ekranını
        // göstermek yerine doğrudan kilit ekranına yönlendiriyoruz - yoksa
        // çocuk uygulamamızı açıp ayarları PIN'siz değiştirebilir. Hangi
        // moda geçeceğimizi GERÇEK duruma göre seçiyoruz - süre gerçekten
        // dolmadıysa "süre doldu" değil, kalan süreyi gösteren normal kilit
        // ekranını açıyoruz (önceden burada mod yanlışlıkla hep "süre
        // doldu" olarak sabitti).
        if (SessionManager.isActive(this)) {
            val mode = if (SessionManager.isTimeUp(this)) {
                BlockActivity.MODE_TIME_UP
            } else {
                BlockActivity.MODE_BLOCKED_APP
            }
            startActivity(Intent(this, BlockActivity::class.java).apply {
                putExtra(BlockActivity.EXTRA_MODE, mode)
            })
            finish()
            return
        }

        refreshPermissionsCard()
        refreshPinCard()
        loadApps()

        // İzin akışının ilerletilmesi ARTIK burada değil - her adımın
        // (bildirim / açıklama ekranı / erişilebilirlik ayarları / cihaz
        // yöneticisi / kullanım erişimi) kendi registerForActivityResult
        // callback'i var ve akışı SADECE gerçekten o adımdan döndüğümüzde
        // ilerletiyor. onResume()'a güvenmek bazı cihazlarda (video kaydıyla
        // doğrulandı: bu Honor/MagicOS cihazında) güvenilir değildi - dış bir
        // Ayarlar ekranına GEÇERKEN bile erken/sahte bir onResume()
        // tetiklenip "kullanıcı geri döndü" sanılıyor ve aynı izin ekranı
        // bir daha açılıyordu.
    }

    /**
     * Uygulama her açıldığında (izinler tamamlanana kadar), asıl izin akışı
     * ("İzin ver" butonu) başlamadan ÖNCE, bu izinlerin neden gerekli
     * olduğunu kısaca anlatan, 10 saniye boyunca kapatılamayan bir pop-up
     * gösterir. Bu ekran kendisi bir izin istemiyor / bir akış başlatmıyor -
     * sadece bilgilendiriyor, tamamen ayrı ve isteğe bağlı bir adım.
     */
    private fun maybeShowPermissionIntroPopup() {
        if (allCorePermissionsGranted()) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_permission_intro, null)
        val btnContinue = dialogView.findViewById<Button>(R.id.btnIntroContinue)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialog.show()

        object : CountDownTimer(15_000L, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000L).toInt() + 1
                btnContinue.text = getString(R.string.permission_intro_button_wait, secondsLeft)
            }

            override fun onFinish() {
                btnContinue.text = getString(R.string.permission_intro_button_ready)
                btnContinue.isEnabled = true
                btnContinue.setOnClickListener { dialog.dismiss() }
            }
        }.start()
    }

    private fun allCorePermissionsGranted(): Boolean {
        val notificationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return isAccessibilityServiceEnabled() && isDeviceAdminActive() && notificationGranted
    }

    private fun refreshPermissionsCard() {
        val allGranted = isAccessibilityServiceEnabled() && isDeviceAdminActive()
        binding.permissionsCard.visibility = if (allGranted) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun refreshPinCard() {
        if (SessionManager.hasPin(this)) {
            binding.tvPinTitle.text = getString(R.string.pin_title_saved)
            binding.etPin.hint = getString(R.string.pin_hint_change)
        } else {
            binding.tvPinTitle.text = getString(R.string.pin_title_new)
            binding.etPin.hint = getString(R.string.pin_hint_new)
        }

        if (SessionManager.hasRecoveryAnswer(this)) {
            binding.tvRecoveryLabel.text = getString(R.string.recovery_answer_label_saved)
            binding.etRecoveryAnswer.hint = getString(R.string.recovery_answer_field_hint_saved)
        } else {
            binding.tvRecoveryLabel.text = getString(R.string.recovery_answer_label)
            binding.etRecoveryAnswer.hint = getString(R.string.recovery_answer_field_hint)
        }
    }

    /** Butona tek basışta tüm izin adımlarını sırayla başlatan akışı açar. */
    private fun openPermissionFlow() {
        permissionFlowActive = true
        continuePermissionFlowIfActive()
    }

    /**
     * Akış aktifken hâlâ eksik olan İLK izni ister/açar, o adımın kendi
     * registerForActivityResult callback'i tetiklendiğinde tekrar çağrılarak
     * bir sonraki eksiğe geçer. Hepsi tamamsa akışı kapatır.
     */
    private fun continuePermissionFlowIfActive() {
        if (!permissionFlowActive) return
        // Bir adım zaten sonuç bekliyorsa (ör. kullanıcı butona hızlıca iki kere
        // dokunduysa, ya da onResume() ile bir callback aynı anda tetiklendiyse)
        // burada tekrar yeni bir ekran başlatmıyoruz - aynı izin ekranının art
        // arda iki kere açılmasının asıl sebebi buydu.
        if (pendingFlowStep != null) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingFlowStep = FlowStep.NOTIFICATION
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        if (!isAccessibilityServiceEnabled()) {
            pendingFlowStep = FlowStep.ACCESSIBILITY_DISCLOSURE
            accessibilityDisclosureLauncher.launch(Intent(this, AccessibilityDisclosureActivity::class.java))
            return
        }

        if (!isDeviceAdminActive()) {
            pendingFlowStep = FlowStep.DEVICE_ADMIN
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent())
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getString(R.string.device_admin_explanation)
                )
            }
            deviceAdminLauncher.launch(intent)
            return
        }

        // NOT: Kullanım Erişimi izni BİLEREK burada istenmiyor. Kilit
        // mekanizması için gerekli değil - sadece "Son kullanılan"/"En çok
        // kullanılan" sıralaması için kullanılıyor. Herkese baştan 4 izin
        // sormak yerine, sadece bu sıralamalardan birini seçen kullanıcıdan,
        // o an bağlamıyla (onSortModeChanged) isteniyor - bkz. o fonksiyon.

        // Hepsi tamam.
        pendingFlowStep = null
        permissionFlowActive = false
        refreshPermissionsCard()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, MonitorAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        for (component in splitter) {
            if (component.equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    private fun adminComponent() = ComponentName(this, ZKDeviceAdminReceiver::class.java)

    private fun isDeviceAdminActive(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(adminComponent())
    }

    private fun loadApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val allowed = SessionManager.getAllowedApps(this)

        appList = resolveInfos
            .asSequence()
            .map { it.activityInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != packageName } // kendimizi listeye koymuyoruz
            .map { ai ->
                AppInfo(
                    packageName = ai.packageName,
                    label = ai.loadLabel(pm).toString(),
                    icon = ai.loadIcon(pm),
                    allowed = allowed.contains(ai.packageName)
                )
            }
            .toMutableList()

        usageMap = UsageStatsHelper.getUsageMap(this)
        // Kullanım verisi yoksa (izin verilmemiş ya da henüz toplanmamışsa)
        // kullanıma dayalı bir sıralamada takılı kalmayalım, alfabetiğe düşelim.
        if (usageMap.isEmpty() && currentSortMode != SortMode.ALPHABETICAL) {
            setSortModeSilently(SortMode.ALPHABETICAL)
        }

        binding.rvApps.layoutManager = LinearLayoutManager(this)
        val adapter = AppListAdapter(sortedAppList())
        appAdapter = adapter
        binding.rvApps.adapter = adapter
    }

    private fun sortedAppList(): List<AppInfo> = when (currentSortMode) {
        SortMode.ALPHABETICAL -> appList.sortedBy { it.label.lowercase() }
        SortMode.RECENT -> appList.sortedWith(
            compareByDescending<AppInfo> { usageMap[it.packageName]?.lastTimeUsed ?: 0L }
                .thenBy { it.label.lowercase() }
        )
        SortMode.MOST_USED -> appList.sortedWith(
            compareByDescending<AppInfo> { usageMap[it.packageName]?.totalForegroundMs ?: 0L }
                .thenBy { it.label.lowercase() }
        )
    }

    private fun onSortModeChanged(checkedId: Int) {
        val mode = when (checkedId) {
            R.id.rbSortMostUsed -> SortMode.MOST_USED
            R.id.rbSortAlpha -> SortMode.ALPHABETICAL
            else -> SortMode.RECENT
        }

        if (mode != SortMode.ALPHABETICAL && !UsageStatsHelper.hasUsageAccess(this)) {
            // Bu iznin nedenini açıkça anlatan bir onay ekranı gösteriyoruz,
            // aniden sistem ayarlarına atlayıp arkadan kaybolan bir Toast
            // göstermek yerine - kullanıcı "neden bu kadar çok izin
            // isteniyor" hissine kapılmasın diye. Kabul etmezse seçim eski
            // moda geri döner, kilit mekanizması bu izne hiç ihtiyaç duymaz.
            pendingSortMode = mode
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.usage_access_dialog_title))
                .setMessage(getString(R.string.toast_usage_access_needed))
                .setPositiveButton(getString(R.string.usage_access_dialog_positive)) { _, _ ->
                    usageAccessLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                .setNegativeButton(getString(R.string.usage_access_dialog_negative)) { _, _ ->
                    pendingSortMode = null
                }
                .setOnCancelListener { pendingSortMode = null }
                .show()
            // Karar verilene kadar seçimi eski moda geri alıyoruz; kabul edip
            // izni açtıktan sonra usageAccessLauncher callback'i bekleyen
            // modu uygular.
            setSortModeSilently(currentSortMode)
            return
        }

        currentSortMode = mode
        appAdapter?.updateList(sortedAppList())
    }

    /** Radio grubunu, listener'ı tetiklemeden verilen moda göre işaretler. */
    private fun setSortModeSilently(mode: SortMode) {
        currentSortMode = mode
        val id = when (mode) {
            SortMode.MOST_USED -> R.id.rbSortMostUsed
            SortMode.ALPHABETICAL -> R.id.rbSortAlpha
            SortMode.RECENT -> R.id.rbSortRecent
        }
        binding.rgSort.setOnCheckedChangeListener(null)
        binding.rgSort.check(id)
        binding.rgSort.setOnCheckedChangeListener { _, checkedId -> onSortModeChanged(checkedId) }
    }

    private fun selectedMinutes(): Int? {
        if (binding.rbCustom.isChecked) {
            return binding.etCustomMinutes.text.toString().toIntOrNull()
        }
        return when (binding.rgDuration.checkedRadioButtonId) {
            R.id.rb15 -> 15
            R.id.rb30 -> 30
            R.id.rb60 -> 60
            else -> null
        }
    }

    private fun onStartClicked() {
        if (!isAccessibilityServiceEnabled() || !isDeviceAdminActive()) {
            Toast.makeText(this, getString(R.string.toast_open_permissions_first), Toast.LENGTH_SHORT).show()
            refreshPermissionsCard()
            return
        }

        val minutes = selectedMinutes()
        if (minutes == null || minutes <= 0) {
            Toast.makeText(this, getString(R.string.toast_pick_valid_duration), Toast.LENGTH_SHORT).show()
            return
        }

        val enteredPin = binding.etPin.text.toString()
        val enteredRecoveryAnswer = binding.etRecoveryAnswer.text.toString().trim()
        // Kurtarma cevabı, PIN unutulduğunda tek çıkış yolu (bkz.
        // SessionManager'daki açıklama) - bu yüzden henüz belirlenmemişse
        // ZORUNLU. Daha önce belirlenmişse ve alan boş bırakıldıysa
        // mevcut cevap korunuyor, kullanıcı her PIN değişikliğinde tekrar
        // girmek zorunda kalmıyor.
        if (!SessionManager.hasRecoveryAnswer(this) && enteredRecoveryAnswer.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_recovery_answer_required), Toast.LENGTH_LONG).show()
            return
        }

        if (!SessionManager.hasPin(this)) {
            if (enteredPin.length < 4) {
                Toast.makeText(this, getString(R.string.toast_pin_min_length_new), Toast.LENGTH_SHORT).show()
                return
            }
            SessionManager.setPin(this, enteredPin)
        } else if (enteredPin.isNotEmpty()) {
            // Kullanıcı PIN alanına bir şey yazdıysa, bunu "PIN'i değiştir" niyeti sayıyoruz.
            if (enteredPin.length < 4) {
                Toast.makeText(this, getString(R.string.toast_pin_min_length_change), Toast.LENGTH_SHORT).show()
                return
            }
            SessionManager.setPin(this, enteredPin)
        }

        if (enteredRecoveryAnswer.isNotEmpty()) {
            val selectedQuestionIndex = binding.spRecoveryQuestion.selectedItemPosition
            SessionManager.setRecoveryAnswer(this, selectedQuestionIndex, enteredRecoveryAnswer)
            binding.etRecoveryAnswer.text?.clear()
        }

        val allowedPackages = appList.filter { it.allowed }.map { it.packageName }.toSet()
        SessionManager.setAllowedApps(this, allowedPackages)

        commitAndStartLock(minutes)
    }

    /**
     * Kilidi GERÇEKTEN başlatan son adım - bir hazır olma kontrolünden sonra.
     *
     * BUG: Erişilebilirlik izni Ayarlar'da "açık" görünse bile ("bkz.
     * isAccessibilityServiceEnabled), Android'in servisi fiilen bağlayıp
     * olay göndermeye başlaması (onServiceConnected) birkaç yüz milisaniye
     * sürebiliyor - özellikle izin daha YENİ verildiyse (izin akışı bitip
     * hemen "Kilidi başlat"a basıldığında olduğu gibi). Bu kısa pencerede
     * kilit başlatılırsa, servis henüz olay almadığı için çocuğun ilk
     * uygulama/ana ekran geçişi yakalanmadan kaçabiliyordu - ama servis
     * bağlandıktan sonra (birkaç yüz milisaniye içinde) her şey normal
     * çalışıyordu. Bu yüzden asıl kilidi başlatmadan önce servisin fiilen
     * bağlı olduğunu (MonitorAccessibilityService.isRunning) doğruluyoruz;
     * değilse kısa aralıklarla (300ms) birkaç kez tekrar deniyoruz. 3
     * saniye içinde hâlâ bağlanmadıysa (çok nadir bir durum olurdu) yine de
     * devam ediyoruz - kullanıcıyı sonsuza kadar bekletmemek için.
     */
    private fun commitAndStartLock(minutes: Int, attempt: Int = 0) {
        if (!MonitorAccessibilityService.isRunning() && attempt < 10) {
            Handler(Looper.getMainLooper()).postDelayed({
                commitAndStartLock(minutes, attempt + 1)
            }, 300L)
            return
        }

        SessionManager.startSession(this, minutes)
        TimerService.start(this)

        Toast.makeText(this, getString(R.string.toast_lock_started_format, minutes), Toast.LENGTH_SHORT).show()

        goToBlockScreenAndFinish()
    }

    /**
     * Kilit başladığında telefonu normal ana ekrana bırakmak yerine doğrudan
     * kilit ekranına (izin verilen uygulamalar listesine) götürüyoruz. Önceden
     * ana ekrana gidiyorduk ve çocuk gerçek bir uygulama açmayı deneyene kadar
     * (Erişilebilirlik servisi onu yakalayana kadar) ana ekranda widget'lar,
     * bildirim çubuğu, hızlı ayarlar gibi yerlerde serbestçe dolaşabiliyordu -
     * bu istenmeyen bir durumdu.
     */
    private fun goToBlockScreenAndFinish() {
        startActivity(Intent(this, BlockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(BlockActivity.EXTRA_MODE, BlockActivity.MODE_BLOCKED_APP)
        })
        finish()
    }
}
