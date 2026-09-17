package com.zamankilidi.app

import android.app.Activity
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.zamankilidi.app.databinding.ActivityAccessibilityDisclosureBinding

/**
 * Erişilebilirlik iznini istemeden ÖNCE gösterilen "prominent disclosure"
 * ekranı: Paydos'un bu izinle tam olarak ne yaptığını / ne yapmadığını
 * açıkça anlatır. Google Play'in, Erişilebilirlik API'sini engelleme amaçlı
 * kullanan uygulamalardan istediği açık onay adımı budur - kullanıcı
 * "Anladım, devam et"e basmadan sistemin Erişilebilirlik ayarları ekranı
 * açılmaz.
 */
class AccessibilityDisclosureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccessibilityDisclosureBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccessibilityDisclosureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnDisclosureContinue.setOnClickListener {
            setResult(Activity.RESULT_OK)
            finish()
        }

        binding.btnDisclosureCancel.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }
}
