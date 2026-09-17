# Zaman Kilidi

Ebeveynin telefonu çocuğuna belirli bir süreliğine, sadece seçtiği uygulamalara
izin vererek ödünç vermesini sağlayan Android uygulaması. Süre dolunca ya da
çocuk izin verilmeyen bir uygulamayı açmaya çalışınca, tam ekran bir uyarı
devreye giriyor ve sadece ebeveynin PIN'iyle kapanıyor.

## Nasıl çalışıyor (özet)

- **MainActivity**: Ebeveyn süreyi seçiyor, telefondaki uygulamalar
  listesinden izin verilecekleri işaretliyor, bir PIN belirliyor.
- **TimerService**: Arka planda geri sayımı tutan bir servis, bildirimde
  kalan süreyi gösteriyor.
- **MonitorAccessibilityService**: Ekranda hangi uygulamanın açık olduğunu
  sürekli izliyor. Süre dolmuşsa ya da izinsiz bir uygulama açılmışsa,
  anında engelleme ekranını devreye sokuyor.
- **BlockActivity**: Hem "süre doldu" hem "bu uygulamaya izin yok" durumunda
  gösterilen tam ekran; geri tuşuyla kapanmıyor, sadece PIN ile kapanıyor
  (izinli uygulamalardan birine dokunmak da bu ekrandan çıkarıyor).
- **ZKDeviceAdminReceiver**: Uygulamanın rastgele/kolayca silinmesini
  zorlaştıran "cihaz yöneticisi" kaydı.

## Kurulum — Android Studio

1. [developer.android.com/studio](https://developer.android.com/studio)
   adresinden **Android Studio**'yu indir, kur. Kurulum sihirbazı ilk açılışta
   gerekli Android SDK'yı bilgisayara kendisi indirecek (birkaç GB,
   internet hızına göre 10-30 dakika sürebilir).
2. Android Studio açılınca **Open** (ya da **Open an Existing Project**) de,
   bu klasörü (`zamankilidi`) seç.
3. Alt tarafta "Gradle Sync" ilerleme çubuğu görünecek, bitmesini bekle (ilk
   seferde bağımlılıkları indirdiği için birkaç dakika sürebilir).
4. Telefonda **Geliştirici Seçenekleri**'ni aç (Ayarlar > Telefon Hakkında >
   "Yapı Numarası"na 7 kere art arda dokun), oradan **USB Hata Ayıklama**'yı
   aç, telefonu bilgisayara USB ile bağla.
5. Android Studio'nun üst çubuğunda telefonun ismini seçili göreceksin, yanındaki
   yeşil **▶ Run** butonuna bas. Birkaç dakika içinde uygulama telefona
   kurulup açılacak.
6. Uygulama içinde önce **"İzinleri ayarla"** butonuna bas — sırayla
   Erişilebilirlik ayarına ve Cihaz Yöneticisi onayına yönlendirecek, ikisini
   de aç/onayla.
7. Süreyi seç, izin verilecek uygulamaları işaretle, bir PIN belirle,
   **"Kilidi başlat"**a bas. Telefonu çocuğa verebilirsin.

USB kablo yoksa: Android Studio'nun kendi telefon **emülatörü** ile de
(gerçek telefon olmadan) denenebilir — "Run" butonunun yanındaki cihaz
listesinden "Create Device" ile sanal bir telefon oluşturulabilir, ama
gerçek kullanım için sonunda gerçek bir telefonda denenmesi gerekir.

## Bilinen sınırlar (bilinçli olarak basit tutuldu)

- **Telefonu yeniden başlatma**: Çocuk telefonu kapatıp açarsa kilit sıfırlanır
  (bilinçli olarak eklenmedi — ileride eklenebilir).
- **Kapsamlı test edilmedi**: Gerçek cihazda ilk kurulumda küçük düzeltmeler
  gerekebilir, özellikle Android sürümüne göre engelleme ekranının araya
  girme hızında farklar olabilir.
- **Play Store'a değil, doğrudan kurulum**: Şu an bu şekilde (Android Studio'dan
  "Run" ile) kuruluyor. İleride gerçek bir `.apk`/`.aab`'ye dönüştürüp
  Play Store'a da konulabilir.
