package com.zamankilidi.app

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.zamankilidi.app.databinding.ActivityBlockBinding

/**
 * Hem "süre doldu" hem de "bu uygulamaya izin yok" durumlarında gösterilen
 * tek ekran. Geri tuşu kasıtlı olarak hiçbir şey yapmıyor - çocuğun bu
 * ekrandan çıkmasının tek yolu ya izinli bir uygulamaya dokunmak (blocked_app
 * modunda) ya da bir büyüğün PIN girmesi.
 */
class BlockActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_TIME_UP = "time_up"
        const val MODE_BLOCKED_APP = "blocked_app"

        // AdMob panelinden alınan geçiş (interstitial) reklam birimi kimliği.
        // Bu reklam SADECE doğru PIN girilip "Kilidi Kaldır"a basıldığında
        // gösterilir - yani yalnızca PIN'i bilen ebeveyn görür, çocuk PIN'i
        // bilmediği için bu ekrana hiç ulaşamaz.
        private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-2569162850712494/6714926004"

        // Banner reklam - SADECE pinRow ile aynı anda görünür (bkz.
        // setPinAreaVisible). blocked_app modunda bu, "Ebeveyn misiniz?"
        // onaylandıktan sonra demek; time_up modunda ise zaten baştan.
        // Çocuğun normalde gördüğü ekranda banner de yoktur.
        private const val BANNER_AD_UNIT_ID = "ca-app-pub-2569162850712494/3062465925"
    }

    private lateinit var binding: ActivityBlockBinding
    private var countDownTimer: CountDownTimer? = null

    // Önceden yüklenmiş geçiş reklamı. PIN doğru girilip kilit kaldırılırken
    // hazırsa gösterilir; hazır değilse akış hiç beklemeden devam eder -
    // reklam asla kilidin kaldırılmasını geciktirmez.
    private var interstitialAd: InterstitialAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Kasıtlı olarak boş - geri tuşu bu ekranı kapatmasın.
            }
        })

        setupForMode()
        setupPinRow()

        MobileAds.initialize(this) {}
        loadInterstitialAd()
        binding.adViewBlock.loadAd(AdRequest.Builder().build())
    }

    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            INTERSTITIAL_AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        binding.adViewBlock.resume()
        // Oturum bir şekilde (ör. başka bir yoldan) zaten bittiyse ekranı kapat.
        if (!SessionManager.isActive(this)) {
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        binding.adViewBlock.pause()
    }

    // pinRow, "PIN'imi unuttum" linki ve banner reklamı her zaman birlikte
    // görünür/gizlenir - hiçbiri PIN alanından bağımsız, tek başına
    // gösterilmez.
    private fun setPinAreaVisible(visible: Boolean) {
        val v = if (visible) android.view.View.VISIBLE else android.view.View.GONE
        binding.pinRow.visibility = v
        binding.tvForgotPin.visibility = v
        binding.adViewBlock.visibility = v
    }

    private fun setupForMode() {
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_TIME_UP

        if (mode == MODE_TIME_UP) {
            binding.tvIcon.text = "⏳"
            binding.tvTitle.text = getString(R.string.time_up_title)
            binding.tvMessage.text = getString(R.string.time_up_message)
            binding.rvAllowedApps.visibility = android.view.View.GONE
            binding.tvParentToggle.visibility = android.view.View.GONE
            setPinAreaVisible(true)
        } else {
            binding.tvIcon.text = "⏸️"
            binding.tvTitle.text = getString(R.string.blocked_app_title)
            bindRemainingTimeMessage()
            setupAllowedAppsList()
            binding.tvParentToggle.visibility = android.view.View.VISIBLE
            setPinAreaVisible(false)
            binding.tvParentToggle.setOnClickListener {
                setPinAreaVisible(true)
                binding.tvParentToggle.visibility = android.view.View.GONE
            }
        }
    }

    private fun bindRemainingTimeMessage() {
        countDownTimer?.cancel()
        val remaining = SessionManager.remainingMillis(this)
        if (remaining <= 0) {
            binding.tvMessage.text = getString(R.string.time_up_recent)
            return
        }
        countDownTimer = object : CountDownTimer(remaining, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val m = (millisUntilFinished / 60000L).toInt()
                val s = ((millisUntilFinished / 1000L) % 60L).toInt()
                binding.tvMessage.text = getString(R.string.remaining_time_format, m, s)
            }

            override fun onFinish() {
                // Ekran hâlâ "blocked_app" intent'iyle açık ama süre bu sırada
                // doldu - intent'in modunu güncelleyip time_up görünümüne geçiyoruz.
                intent.putExtra(EXTRA_MODE, MODE_TIME_UP)
                setupForMode()
            }
        }.also { it.start() }
    }

    private fun setupAllowedAppsList() {
        val pm = packageManager
        val allowedPackages = SessionManager.getAllowedApps(this)
        val apps = allowedPackages.mapNotNull { pkg ->
            try {
                val ai = pm.getApplicationInfo(pkg, 0)
                AppInfo(pkg, ai.loadLabel(pm).toString(), ai.loadIcon(pm), allowed = true)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }.sortedBy { it.label.lowercase() }

        binding.rvAllowedApps.visibility = if (apps.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        binding.rvAllowedApps.layoutManager = GridLayoutManager(this, 4)
        binding.rvAllowedApps.adapter = AllowedAppAdapter(apps) { app ->
            val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null) {
                startActivity(launchIntent)
                finish()
            }
        }
    }

    private fun setupPinRow() {
        binding.btnUnlock.setOnClickListener {
            val pin = binding.etPin.text.toString()
            if (SessionManager.checkPin(this, pin)) {
                showInterstitialThenUnlock()
            } else {
                binding.tvPinError.visibility = android.view.View.VISIBLE
                binding.etPin.text?.clear()
            }
        }
        binding.tvForgotPin.setOnClickListener { showRecoveryDialog() }
    }

    // PIN unutulduğunda, Ayarlar'a gitmeye gerek kalmadan (bu oturum aktifken
    // zaten mümkün değil - bkz. MonitorAccessibilityService) doğrudan burada,
    // PIN belirlenirken kaydedilen kurtarma cevabıyla kilidi kaldırmayı
    // sağlar.
    private fun showRecoveryDialog() {
        if (!SessionManager.hasRecoveryAnswer(this)) {
            Toast.makeText(this, getString(R.string.recovery_not_set), Toast.LENGTH_LONG).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_recovery_answer, null)
        val spinner = dialogView.findViewById<Spinner>(R.id.spRecoveryQuestionDialog)
        val input = dialogView.findViewById<EditText>(R.id.etRecoveryAnswerDialog)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.recovery_dialog_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.recovery_dialog_positive)) { _, _ ->
                val selectedIndex = spinner.selectedItemPosition
                val answer = input.text.toString()
                if (SessionManager.checkRecoveryAnswer(this, selectedIndex, answer)) {
                    showInterstitialThenUnlock()
                } else {
                    Toast.makeText(this, getString(R.string.recovery_wrong_answer), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.recovery_dialog_negative), null)
            .show()
    }

    // Doğru PIN girildiği doğrulandığı AN (yani sadece ebeveyn bu noktaya
    // ulaşabilir) kilidi hemen kaldırıyoruz - reklamın gösterilip
    // gösterilmediğini ya da nasıl kapandığını beklemeden. Reklamı bundan
    // SONRA, sadece "bonus" bir ekran olarak gösteriyoruz.
    //
    // ÖNEMLİ: Eskiden kilit, reklam kapanana (onAdDismissedFullScreenContent)
    // kadar kaldırılmıyordu. Kullanıcı reklama tıklayıp Play Store/tarayıcı
    // gibi başka bir uygulamaya geçtiğinde, oturum hâlâ "aktif" göründüğü
    // için MonitorAccessibilityService o uygulamayı izinsiz sayıp tekrar bu
    // ekrana geri yönlendiriyordu - yani doğru PIN girilmesine rağmen kilit
    // reklam ekranı tamamen kapanana kadar "çalışmaya" devam ediyormuş gibi
    // görünüyordu. Artık oturum reklamdan ÖNCE bittiği için bu sorun oluşmaz.
    private fun showInterstitialThenUnlock() {
        SessionManager.endSession(this)
        TimerService.stop(this)

        val ad = interstitialAd
        if (ad == null) {
            finish()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                finish()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitialAd = null
                finish()
            }
        }
        ad.show(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        binding.adViewBlock.destroy()
    }
}
