# R8 kuralları (minifyEnabled true).
#
# Bu uygulamada yansıma (reflection), JSON serileştirme ya da sınıf adına
# göre dinamik yükleme KULLANILMIYOR; bu yüzden neredeyse hiç "keep" kuralı
# gerekmiyor:
#
#   - AndroidManifest.xml'de adı geçen sınıfları (MainActivity, BlockActivity,
#     AccessibilityDisclosureActivity, TimerService, MonitorAccessibilityService,
#     BootReceiver, ZKDeviceAdminReceiver) R8 zaten otomatik koruyor.
#   - AdMob, AndroidX ve Material kendi kurallarını kitaplıkla birlikte
#     (consumer rules) getiriyor, burada tekrarlamaya gerek yok.
#   - ViewBinding sınıfları koda doğrudan referansla erişildiği için korunuyor.
#
# Yeni bir kitaplık eklenirse ya da bir yere reflection girerse buraya kural
# eklemek gerekebilir - R8 hataları yalnızca imzalı release derlemesinde
# ortaya çıkar, debug derlemesi hiçbir şey söylemez.

# Çökme raporlarının okunabilir kalması için satır numaralarını sakla.
# Bunlar olmadan Play Console'daki yığın izleri karartılmış sınıf adlarından
# ibaret kalır ve hata ayıklamak imkansızlaşır.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
