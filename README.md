# Paydos — Çocuk Ekran Kilidi

Ebeveynin telefonunu çocuğuna belirli bir süreliğine, sadece seçtiği
uygulamalara izin vererek ödünç vermesini sağlayan Android uygulaması. Süre
dolunca ya da çocuk izin verilmeyen bir uygulamayı açmaya çalışınca tam ekran
bir uyarı devreye giriyor ve sadece ebeveynin PIN'iyle kapanıyor.

Paket adı `com.zamankilidi.app`, arayüz 9 dilde
(tr, en, ar, es, fr, hi, in, pt-BR, ru).

## Nasıl çalışıyor

| Bileşen | İşi |
|---|---|
| `MainActivity` | Ebeveyn süreyi seçiyor, izinli uygulamaları işaretliyor, PIN ve kurtarma sorusu belirliyor |
| `SessionManager` | Oturum durumu, izinli uygulamalar, PIN ve kurtarma cevabı — hepsi `SharedPreferences`'ta |
| `TimerService` | Geri sayımı tutan ön plan servisi, kalan süreyi bildirimde gösteriyor |
| `MonitorAccessibilityService` | Ekrandaki uygulamanın paket adını izliyor, izinsizse kilit ekranını açıyor |
| `BlockActivity` | "Süre doldu" ve "bu uygulamaya izin yok" ekranı; geri tuşuyla kapanmıyor |
| `BootReceiver` | Yeniden başlatmada aktif oturumu kapatıyor (bkz. aşağıdaki tasarım kararları) |
| `ZKDeviceAdminReceiver` | Cihaz yöneticisi kaydı — uygulamanın kaldırılmasını zorlaştırıyor |

PIN ve kurtarma cevabı düz metin olarak değil, her biri kendi 16 baytlık
tuzuyla SHA-256 özeti olarak saklanıyor.

## İzinler

`AndroidManifest.xml` ile birebir tutulmalı; gizlilik politikasındaki liste de
buradan besleniyor.

| İzin | Ne için |
|---|---|
| `BIND_ACCESSIBILITY_SERVICE` | Ekrandaki uygulamanın paket adını görmek (içerik okunmuyor) |
| `BIND_DEVICE_ADMIN` | Kaldırmayı zorlaştırmak |
| `PACKAGE_USAGE_STATS` | İsteğe bağlı: uygulama listesini son kullanıma göre sıralamak |
| `POST_NOTIFICATIONS` | Kalan süreyi gösteren sabit bildirim |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Geri sayımın arka planda sürmesi |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Pil tasarrufunun geri sayımı durdurmaması |
| `RECEIVE_BOOT_COMPLETED` | Yeniden başlatmada oturumu **kapatmak** için |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Yalnızca AdMob reklam isteği |

## Bilinçli tasarım kararları

**Yeniden başlatma kilidi kaldırır.** `BootReceiver` boot'ta oturumu
sonlandırıyor. Bu bir emniyet supabı: PIN'ini ve kurtarma sorusunun cevabını
birlikte unutan bir ebeveynin telefonu kalıcı olarak kilitli kalmasın diye.
Karşılığında çocuk telefonu kapatıp açarak kilitten çıkabiliyor; bu takas
bilerek kabul edildi. Alıcı olmadan davranış üreticiye kalıyordu — MagicOS/MIUI
gibi arayüzler erişilebilirlik servisini boot'ta kapattığı için kilit orada
kendiliğinden düşüyor, stock Android'de ise devam ediyordu.

**Reklamlar ebeveyne yönelik.** `MainActivity`'de banner, doğru PIN'den sonra
geçiş reklamı, kilit ekranında ise yalnızca "Ebeveyn misiniz?" bağlantısıyla
açılan banner. Çocuğun süre dolduğunda gördüğü ekranda reklam yok; kilit
ekranındaki banner PIN alanı açılmadan yüklenmiyor bile. Bu bağlantı bir PIN
kapısı değil — çocuk da dokunabilir. Daha sıkısı gerekirse banner'ı o ekrandan
tamamen çıkarmak gerekir.

**Uçtan uca ekran elle yönetiliyor.** `targetSdk 36` olduğu için Android 15+
içeriği sistem çubuklarının altına çiziyor ve temadaki `statusBarColor` /
`navigationBarColor` ayarlarını yok sayıyor. O iki satır temadan kaldırıldı;
çubukların kapladığı alan `EdgeToEdge.kt` içinde dolgu olarak ekleniyor ve
simgeler açık renge zorlanıyor (uygulama her zaman koyu, `values-night` yok).

**Ana ekran da kontrol ediliyor.** Launcher paketi izinli sayılmıyor; oturum
aktifken ana ekrana dönmek de "izinli mi" kontrolünden geçiyor.

## Derleme

Gradle wrapper deposunda yok; Android Studio'da açıp derlemek en kolayı.
Komut satırından derleyeceksen kendi Gradle kurulumunla (8.7 ile test edildi)
ve **JDK 17 ya da 21** ile:

```bash
JAVA_HOME=~/.jdks/jbr-17.0.14 gradle assembleRelease
```

`JAVA_HOME` gerçekten gerekli: Android Studio'nun kendi JBR'si (`Android
Studio/jbr`) 2026 sürümlerinde Java 25'e yükseldi ve Gradle 8.7 onu
tanımıyor — `Unsupported class file major version 69` hatası bundan geliyor.
Gradle'ı da yükseltmediğimiz sürece ayrı bir JDK 17/21 göstermek gerekiyor.

`local.properties` içindeki `sdk.dir` Android SDK yolunu göstermeli.

## Release imzalama

İmzalama bilgileri proje kökündeki `keystore.properties` dosyasından okunuyor;
bu dosya ve `.jks` **depoya girmiyor** (`.gitignore`). Kurulum için
`keystore.properties.example` dosyasındaki adımları izle. Dosya yoksa release
build imzasız derlenir, hata vermez.

Yeni sürüm çıkarırken `app/build.gradle` içindeki `versionCode` artırılmalı.

Release derlemesinde R8 açık (`minifyEnabled true` + `shrinkResources true`).
Kurallar `app/proguard-rules.pro` içinde ve neredeyse boş — uygulamada
yansıma yok, manifest'teki sınıfları R8 zaten koruyor. **R8 hataları yalnızca
imzalı release derlemesinde ortaya çıkar**, debug derlemesi hiçbir şey
söylemez; o yüzden her sürümde release APK'yi cihazda bir kez açmak şart.
Çökme raporlarını çözmek için `app/build/outputs/mapping/release/mapping.txt`
Play Console'a yüklenmeli.

## Gizlilik politikası

https://fmjapps.github.io/privacy/paydos/ — kaynağı `fmjapps/privacy`
deposunda `paydos/index.html`. İzin listesi değişirse politika da
güncellenmeli.
